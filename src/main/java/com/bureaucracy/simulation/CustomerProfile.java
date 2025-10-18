package com.bureaucracy.simulation;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class CustomerProfile {
    private final String customerId;
    private final List<String> requestedDocuments;
    private final Duration arrivalDelay;

    public CustomerProfile(String customerId, List<String> requestedDocuments, Duration arrivalDelay) {
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.requestedDocuments = List.copyOf(Objects.requireNonNull(requestedDocuments, "requestedDocuments"));
        this.arrivalDelay = Objects.requireNonNull(arrivalDelay, "arrivalDelay");
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<String> getRequestedDocuments() {
        return requestedDocuments;
    }

    public Duration getArrivalDelay() {
        return arrivalDelay;
    }
}
