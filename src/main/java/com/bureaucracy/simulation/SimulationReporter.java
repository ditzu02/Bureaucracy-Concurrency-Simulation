package com.bureaucracy.simulation;

public interface SimulationReporter extends OfficeEventListener {
    void customerEvent(String customerId, String message);

    void systemEvent(String message);

    void documentIssued(DocumentProcessingResult result);
}
