package com.bureaucracy.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration describing the offices, documents, and dependencies used for the simulation.
 */
public final class SimulationConfig {

    private final Map<String, OfficeConfig> officesByName;
    private final Map<String, DocumentConfig> documentsByName;

    public SimulationConfig(List<OfficeConfig> offices, List<DocumentConfig> documents) {
        Objects.requireNonNull(offices, "offices");
        Objects.requireNonNull(documents, "documents");

        Map<String, OfficeConfig> officeMap = new HashMap<>();
        for (OfficeConfig office : offices) {
            officeMap.put(office.getName(), office);
        }
        this.officesByName = Collections.unmodifiableMap(officeMap);

        Map<String, DocumentConfig> documentMap = new HashMap<>();
        for (DocumentConfig document : documents) {
            documentMap.put(document.getName(), document);
        }
        this.documentsByName = Collections.unmodifiableMap(documentMap);
    }

    public List<OfficeConfig> getOffices() {
        return new ArrayList<>(officesByName.values());
    }

    public OfficeConfig getOffice(String name) {
        return officesByName.get(name);
    }

    public DocumentConfig getDocument(String name) {
        return documentsByName.get(name);
    }

    public List<DocumentConfig> getAllDocuments() {
        return new ArrayList<>(documentsByName.values());
    }

    /**
     * Provides a default configuration used by the sample simulation run.
     */
    public static SimulationConfig sample() {
        List<OfficeConfig> offices = List.of(
                new OfficeConfig("Identity Office", 2, Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(6)),
                new OfficeConfig("Tax Agency", 2, Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(7)),
                new OfficeConfig("City Hall", 3, Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(8)),
                new OfficeConfig("Health Services", 2, Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(6))
        );

        List<DocumentConfig> documents = List.of(
                new DocumentConfig("ID_FORM", "Identity Office", List.of()),
                new DocumentConfig("CITIZEN_CARD", "Identity Office", List.of("ID_FORM")),
                new DocumentConfig("TAX_ID", "Tax Agency", List.of("ID_FORM")),
                new DocumentConfig("TAX_CLEARANCE", "Tax Agency", List.of("TAX_ID")),
                new DocumentConfig("HEALTH_INSURANCE", "Health Services", List.of("ID_FORM")),
                new DocumentConfig("RESIDENCY_CERT", "City Hall", List.of("CITIZEN_CARD", "TAX_CLEARANCE")),
                new DocumentConfig("BUSINESS_LICENSE", "City Hall", List.of("RESIDENCY_CERT", "TAX_CLEARANCE", "HEALTH_INSURANCE"))
        );

        return new SimulationConfig(offices, documents);
    }

    public static final class OfficeConfig {
        private final String name;
        private final int counters;
        private final Duration minServiceTime;
        private final Duration maxServiceTime;
        private final Duration breakDuration;

        public OfficeConfig(String name, int counters, Duration minServiceTime, Duration maxServiceTime, Duration breakDuration) {
            this.name = Objects.requireNonNull(name, "name");
            if (counters <= 0) {
                throw new IllegalArgumentException("counters must be > 0");
            }
            this.counters = counters;
            this.minServiceTime = Objects.requireNonNull(minServiceTime, "minServiceTime");
            this.maxServiceTime = Objects.requireNonNull(maxServiceTime, "maxServiceTime");
            this.breakDuration = Objects.requireNonNull(breakDuration, "breakDuration");
        }

        public String getName() {
            return name;
        }

        public int getCounters() {
            return counters;
        }

        public Duration getMinServiceTime() {
            return minServiceTime;
        }

        public Duration getMaxServiceTime() {
            return maxServiceTime;
        }

        public Duration getBreakDuration() {
            return breakDuration;
        }
    }

    public static final class DocumentConfig {
        private final String name;
        private final String issuingOffice;
        private final List<String> dependencies;

        public DocumentConfig(String name, String issuingOffice, List<String> dependencies) {
            this.name = Objects.requireNonNull(name, "name");
            this.issuingOffice = Objects.requireNonNull(issuingOffice, "issuingOffice");
            this.dependencies = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(dependencies, "dependencies")));
        }

        public String getName() {
            return name;
        }

        public String getIssuingOffice() {
            return issuingOffice;
        }

        public List<String> getDependencies() {
            return dependencies;
        }
    }
}
