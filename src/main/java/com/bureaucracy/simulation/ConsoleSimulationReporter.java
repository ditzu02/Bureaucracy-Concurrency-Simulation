package com.bureaucracy.simulation;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ConsoleSimulationReporter implements SimulationReporter {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final PrintWriter fileWriter;

    public ConsoleSimulationReporter() {
        try {
            Path logPath = Path.of("simulation.log");
            fileWriter = new PrintWriter(
                    Files.newBufferedWriter(
                            logPath,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                    ),
                    true
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to initialize simulation log file", e);
        }
    }

    @Override
    public void onEvent(String officeName, String message) {
        writeToFile("OFF:" + officeName, message);
    }

    @Override
    public void customerEvent(String customerId, String message) {
        writeToFile("CUS:" + customerId, message);
    }

    @Override
    public void systemEvent(String message) {
        writeToFile("SYS", message);
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
        writeToFile("DOC:" + result.getCustomerId(), msg);
    }

    @Override
    public void officeArrival(String officeName, String customerId, String documentName) {
        String text = "ARRIVE office %s person %s asking for %s".formatted(officeName, customerId, documentName);
        printToConsole(text);
        writeToFile("ARRIVE", text);
    }

    @Override
    public void requestAccepted(String officeName, String customerId, String documentName) {
        String text = "REQUEST office %s person %s -> %s in progress".formatted(officeName, customerId, documentName);
        printToConsole(text);
        writeToFile("REQUEST", text);
    }

    @Override
    public void queueEvent(String officeName,
                           String customerId,
                           String documentName,
                           java.util.List<String> queueSnapshot) {
        String people = queueSnapshot.isEmpty()
                ? "(now being served)"
                : String.join(", ", queueSnapshot);
        String text = "QUEUE office %s person %s waiting for %s | line: %s"
                .formatted(officeName, customerId, documentName, people);
        printToConsole(text);
        writeToFile("QUEUE", text);
    }

    @Override
    public void arrivalEvent(String officeName, int counterIndex, String customerId, String documentName) {
        String text = "COUNTER office %s counter %d now processing person %s for %s"
                .formatted(officeName, counterIndex, customerId, documentName);
        printToConsole(text);
        writeToFile("ARRIVAL", text);
    }

    @Override
    public void transportEvent(String requesterOffice, String handlerOffice, String documentName) {
        String text = "TRANSPORTING from counter: %s to counter: %s document: %s"
                .formatted(requesterOffice, handlerOffice, documentName);
        printToConsole(text);
        writeToFile("TRANSPORT", text);
    }

    @Override
    public void cancelEvent(String officeName, String customerId, String documentName, String reason) {
        String text = "CANCELLED at office %s person %s request %s -> %s"
                .formatted(officeName, customerId, documentName, reason);
        printToConsole(text);
        writeToFile("CANCEL", text);
    }

    @Override
    public void finishEvent(String officeName, int counterIndex, String customerId, String documentName) {
        String text = "FINISHED person %s got %s from %s counter %d LEAVING..."
                .formatted(customerId, documentName, officeName, counterIndex);
        printToConsole(text);
        writeToFile("FINISH", text);
    }

    @Override
    public void close() {
        synchronized (fileWriter) {
            fileWriter.flush();
            fileWriter.close();
        }
    }

    private void writeToFile(String channel, String message) {
        String timestamp = formatter.format(java.time.Instant.now()
                .atZone(ZoneId.systemDefault())
                .toLocalTime());
        synchronized (fileWriter) {
            fileWriter.println(timestamp + " [" + channel + "] " + message);
        }
    }

    private void printToConsole(String message) {
        System.out.println(message);
    }
}
