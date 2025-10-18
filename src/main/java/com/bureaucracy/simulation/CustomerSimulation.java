package com.bureaucracy.simulation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Simulates a customer progressing through the bureaucratic system.
 */
public final class CustomerSimulation implements Runnable {
    private final CustomerProfile profile;
    private final DocumentManager documentManager;
    private final SimulationReporter reporter;

    public CustomerSimulation(CustomerProfile profile, DocumentManager documentManager, SimulationReporter reporter) {
        this.profile = profile;
        this.documentManager = documentManager;
        this.reporter = reporter;
    }

    @Override
    public void run() {
        reporter.customerEvent(profile.getCustomerId(), "arrived at the bureaucracy building");

        DocumentManager.CustomerJourney journey = documentManager.createJourney(profile.getCustomerId());
        List<CompletableFuture<DocumentProcessingResult>> pending = new ArrayList<>();
        for (String doc : profile.getRequestedDocuments()) {
            reporter.customerEvent(profile.getCustomerId(), "needs document " + doc);
            pending.add(journey.requestDocument(doc));
        }

        List<DocumentProcessingResult> results = new ArrayList<>();
        Instant start = Instant.now();
        for (CompletableFuture<DocumentProcessingResult> future : pending) {
            try {
                results.add(future.join());
            } catch (Exception ex) {
                reporter.customerEvent(profile.getCustomerId(), "failed to obtain document: " + ex.getMessage());
                return;
            }
        }
        Duration took = Duration.between(start, Instant.now());
        reporter.customerEvent(profile.getCustomerId(), "completed journey in " + took.toSeconds() + "s with " + results.size() + " documents");
    }
}
