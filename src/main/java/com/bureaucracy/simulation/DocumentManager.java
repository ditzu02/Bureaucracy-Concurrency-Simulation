package com.bureaucracy.simulation;

import com.bureaucracy.config.SimulationConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Orchestrates document issuance across multiple offices while respecting dependencies.
 */
public class DocumentManager {

    private final SimulationConfig config;
    private final Map<String, Office> officesByName;
    private final SimulationReporter reporter;

    public DocumentManager(SimulationConfig config, Map<String, Office> officesByName, SimulationReporter reporter) {
        this.config = Objects.requireNonNull(config, "config");
        this.officesByName = Objects.requireNonNull(officesByName, "officesByName");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    public CustomerJourney createJourney(String customerId) {
        return new CustomerJourney(customerId, this);
    }

    CompletableFuture<DocumentProcessingResult> scheduleDocument(CustomerJourney journey, String documentName) {
        SimulationConfig.DocumentConfig docConfig = Objects.requireNonNull(
                config.getDocument(documentName),
                () -> "Unknown document: " + documentName
        );
        Office office = Objects.requireNonNull(
                officesByName.get(docConfig.getIssuingOffice()),
                () -> "Unknown office: " + docConfig.getIssuingOffice()
        );
        reporter.customerEvent(journey.getCustomerId(),
                "queued " + documentName + " at " + office.getName() +
                        describeDependencies(docConfig.getDependencies()));
        CompletableFuture<DocumentProcessingResult> resultFuture = new CompletableFuture<>();
        submitWithRetry(journey, docConfig, office, resultFuture);
        return resultFuture;
    }

    private void submitWithRetry(CustomerJourney journey,
                                 SimulationConfig.DocumentConfig docConfig,
                                 Office office,
                                 CompletableFuture<DocumentProcessingResult> target) {
        if (target.isDone()) {
            return;
        }
        reporter.officeArrival(office.getName(), journey.getCustomerId(), docConfig.getName());
        IssuanceTask task = new IssuanceTask(journey.getCustomerId(), docConfig.getName(), () -> {
            List<String> missing = new ArrayList<>();
            for (String dependency : docConfig.getDependencies()) {
                if (!journey.hasDocument(dependency)) {
                    missing.add(dependency);
                }
            }
            if (!missing.isEmpty()) {
                throw new MissingDependenciesException(missing);
            }
            return new DocumentProcessingResult(
                    journey.getCustomerId(),
                    docConfig.getName(),
                    office.getName(),
                    docConfig.getDependencies(),
                    Duration.ZERO
            );
        });

        CompletableFuture<DocumentProcessingResult> officeFuture;
        try {
            officeFuture = office.submit(task);
            reporter.requestAccepted(office.getName(), journey.getCustomerId(), docConfig.getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            target.completeExceptionally(e);
            return;
        }

        officeFuture.whenComplete((result, error) -> {
            if (result != null) {
                reporter.documentIssued(result);
                target.complete(result);
                return;
            }
            Throwable cause = (error instanceof CompletionException ce && ce.getCause() != null)
                    ? ce.getCause()
                    : error;
            if (cause instanceof MissingDependenciesException missing) {
                String reason = "needs " + String.join(", ", missing.getMissing());
                reporter.cancelEvent(office.getName(), journey.getCustomerId(), docConfig.getName(), reason);
                reporter.customerEvent(journey.getCustomerId(),
                        "missing " + String.join(", ", missing.getMissing()) + " before " + docConfig.getName());
                resolveDependencies(journey, docConfig, missing.getMissing())
                        .whenComplete((ignored, depError) -> {
                            if (depError != null) {
                                target.completeExceptionally(depError);
                            } else {
                                submitWithRetry(journey, docConfig, office, target);
                            }
                        });
            } else if (cause != null) {
                reporter.customerEvent(journey.getCustomerId(),
                        "failed to obtain " + docConfig.getName() + ": " + cause.getMessage());
                target.completeExceptionally(cause);
            } else {
                target.completeExceptionally(new IllegalStateException("Unknown failure issuing " + docConfig.getName()));
            }
        });
    }

    private CompletableFuture<Void> resolveDependencies(CustomerJourney journey,
                                                        SimulationConfig.DocumentConfig parentConfig,
                                                        List<String> dependencies) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String dependency : dependencies) {
            SimulationConfig.DocumentConfig dependencyConfig = Objects.requireNonNull(
                    config.getDocument(dependency),
                    () -> "Unknown dependency: " + dependency
            );
            chain = chain.thenCompose(ignored -> {
                reporter.customerEvent(journey.getCustomerId(),
                        "needs dependency " + dependency + " before " + parentConfig.getName());
                reporter.transportEvent(parentConfig.getIssuingOffice(), dependencyConfig.getIssuingOffice(), dependency);
                return journey.requestDocument(dependency).thenApply(r -> null);
            });
        }
        return chain;
    }

    private static String describeDependencies(List<String> deps) {
        if (deps.isEmpty()) {
            return " (no prerequisites)";
        }
        return " (requires: " + String.join(", ", deps) + ")";
    }

    /**
     * Tracks the documents obtained by a customer and prevents duplicate work.
     */
    public static final class CustomerJourney {
        private final String customerId;
        private final DocumentManager manager;
        private final ConcurrentMap<String, CompletableFuture<DocumentProcessingResult>> documents = new ConcurrentHashMap<>();

        private CustomerJourney(String customerId, DocumentManager manager) {
            this.customerId = Objects.requireNonNull(customerId, "customerId");
            this.manager = manager;
        }

        public String getCustomerId() {
            return customerId;
        }

        public CompletableFuture<DocumentProcessingResult> requestDocument(String documentName) {
            while (true) {
                CompletableFuture<DocumentProcessingResult> existing = documents.get(documentName);
                if (existing != null) {
                    return existing;
                }
                CompletableFuture<DocumentProcessingResult> placeholder = new CompletableFuture<>();
                CompletableFuture<DocumentProcessingResult> previous = documents.putIfAbsent(documentName, placeholder);
                if (previous == null) {
                    manager.scheduleDocument(this, documentName).whenComplete((result, error) -> {
                        if (error != null) {
                            placeholder.completeExceptionally(error);
                        } else {
                            placeholder.complete(result);
                        }
                    });
                    return placeholder;
                }
            }
        }

        boolean hasDocument(String documentName) {
            CompletableFuture<DocumentProcessingResult> future = documents.get(documentName);
            return future != null && future.isDone() && !future.isCompletedExceptionally();
        }
    }

    private static final class MissingDependenciesException extends Exception {
        private final List<String> missing;

        MissingDependenciesException(List<String> missing) {
            super("Missing dependencies: " + missing);
            this.missing = List.copyOf(missing);
        }

        List<String> getMissing() {
            return missing;
        }
    }
}
