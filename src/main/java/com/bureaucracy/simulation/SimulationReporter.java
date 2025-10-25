package com.bureaucracy.simulation;

public interface SimulationReporter extends OfficeEventListener, AutoCloseable {
    void customerEvent(String customerId, String message);

    void systemEvent(String message);

    void documentIssued(DocumentProcessingResult result);

    void officeArrival(String officeName, String customerId, String documentName);

    void requestAccepted(String officeName, String customerId, String documentName);

    void queueEvent(String officeName, String customerId, String documentName, java.util.List<String> queueSnapshot);

    void arrivalEvent(String officeName, int counterIndex, String customerId, String documentName);

    void transportEvent(String requesterOffice, String handlerOffice, String documentName);

    void cancelEvent(String officeName, String customerId, String documentName, String reason);

    void finishEvent(String officeName, int counterIndex, String customerId, String documentName);

    @Override
    default void close() throws Exception {
        // default no-op
    }
}
