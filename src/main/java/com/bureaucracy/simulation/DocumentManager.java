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

    CompletableFuture<DocumentProcessingResult> requestDocument(CustomerJourney journey, String documentName) {
        SimulationConfig.DocumentConfig docConfig = Objects.requireNonNull(
                config.getDocument(documentName),
                () -> "Unknown document: " + documentName
        );

        Office office = Objects.requireNonNull(
                officesByName.get(docConfig.getIssuingOffice()),
                () -> "Unknown office: " + docConfig.getIssuingOffice()
        );

        IssuanceTask task = new IssuanceTask(journey.getCustomerId(), documentName, () -> {
            List<String> dependencies = new ArrayList<>();
            for (String dependency : docConfig.getDependencies()) {
                log(office.getName(), "Requesting dependency " + dependency + " for customer " + journey.getCustomerId());
                DocumentProcessingResult dependencyResult = safeJoin(journey.requestDocument(dependency));
                dependencies.add(dependencyResult.getDocumentName());
            }
            return new DocumentProcessingResult(
                    journey.getCustomerId(),
                    documentName,
                    office.getName(),
                    dependencies,
                    Duration.ZERO
            );
        });

        try {
            CompletableFuture<DocumentProcessingResult> future = office.submit(task);
            future.whenComplete((result, error) -> {
                if (result != null) {
                    reporter.documentIssued(result);
                } else if (error != null) {
                    reporter.customerEvent(journey.getCustomerId(),
                            "failed to obtain " + documentName + ": " + error.getMessage());
                }
            });
            return future;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CompletableFuture<DocumentProcessingResult> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    private void log(String office, String message) {
        reporter.onEvent(office, message);
    }

    private static DocumentProcessingResult safeJoin(CompletableFuture<DocumentProcessingResult> future) throws Exception {
        try {
            return future.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw ex;
        }
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
            return documents.computeIfAbsent(documentName, doc -> manager.requestDocument(this, doc));
        }
    }
}
