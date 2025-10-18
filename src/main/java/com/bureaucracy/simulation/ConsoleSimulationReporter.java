package com.bureaucracy.simulation;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

public final class ConsoleSimulationReporter implements SimulationReporter {

    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_TIME;

    @Override
    public void onEvent(String officeName, String message) {
        log("OFF", officeName, message);
    }

    @Override
    public void customerEvent(String customerId, String message) {
        log("CUS", customerId, message);
    }

    @Override
    public void systemEvent(String message) {
        log("SYS", "", message);
    }

    @Override
    public void documentIssued(DocumentProcessingResult result) {
        Duration duration = result.getServiceDuration();
        String suffix = duration.isZero()
                ? ""
                : " in %d ms".formatted(duration.toMillis());
        String deps = result.getDependencies().isEmpty()
                ? ""
                : " (deps: " + String.join(", ", result.getDependencies()) + ")";
        String msg = "received %s from %s%s%s".formatted(
                result.getDocumentName(),
                result.getIssuingOffice(),
                suffix,
                deps
        );
        customerEvent(result.getCustomerId(), msg);
    }

    private void log(String type, String subject, String message) {
        String timestamp = formatter.format(java.time.Instant.now()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime());
        String label = subject == null || subject.isBlank()
                ? "[%s]".formatted(type)
                : "[%s:%s]".formatted(type, subject);
        System.out.println(timestamp + " " + label + " " + message);
    }
}
