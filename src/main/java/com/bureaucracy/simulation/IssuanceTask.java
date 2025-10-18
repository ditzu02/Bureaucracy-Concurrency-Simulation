package com.bureaucracy.simulation;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Describes a unit of work to be processed by an office counter.
 */
public final class IssuanceTask {
    private final String customerId;
    private final String documentName;
    private final Callable<DocumentProcessingResult> work;

    public IssuanceTask(String customerId, String documentName, Callable<DocumentProcessingResult> work) {
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.documentName = Objects.requireNonNull(documentName, "documentName");
        this.work = Objects.requireNonNull(work, "work");
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public Callable<DocumentProcessingResult> getWork() {
        return work;
    }
}
