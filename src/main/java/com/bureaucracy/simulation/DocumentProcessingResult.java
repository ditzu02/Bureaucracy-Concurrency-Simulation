package com.bureaucracy.simulation;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the result of issuing a document for a specific customer.
 */
public final class DocumentProcessingResult {
    private final String customerId;
    private final String documentName;
    private final String issuingOffice;
    private final List<String> dependencies;
    private final Duration serviceDuration;

    public DocumentProcessingResult(String customerId,
                                    String documentName,
                                    String issuingOffice,
                                    List<String> dependencies,
                                    Duration serviceDuration) {
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.documentName = Objects.requireNonNull(documentName, "documentName");
        this.issuingOffice = Objects.requireNonNull(issuingOffice, "issuingOffice");
        this.dependencies = Collections.unmodifiableList(List.copyOf(Objects.requireNonNull(dependencies, "dependencies")));
        this.serviceDuration = Objects.requireNonNull(serviceDuration, "serviceDuration");
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public String getIssuingOffice() {
        return issuingOffice;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public Duration getServiceDuration() {
        return serviceDuration;
    }

    public DocumentProcessingResult withServiceDuration(Duration duration) {
        return new DocumentProcessingResult(customerId, documentName, issuingOffice, dependencies, duration);
    }

    @Override
    public String toString() {
        return "DocumentProcessingResult{" +
                "customerId='" + customerId + '\'' +
                ", documentName='" + documentName + '\'' +
                ", issuingOffice='" + issuingOffice + '\'' +
                ", dependencies=" + dependencies +
                ", serviceDuration=" + serviceDuration +
                '}';
    }
}
