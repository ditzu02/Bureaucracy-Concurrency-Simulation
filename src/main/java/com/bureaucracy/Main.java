package com.bureaucracy;

import com.bureaucracy.config.SimulationConfig;
import com.bureaucracy.simulation.BureaucracySimulation;
import com.bureaucracy.simulation.ConsoleSimulationReporter;
import com.bureaucracy.simulation.CustomerProfile;
import com.bureaucracy.simulation.SimulationReporter;

import java.time.Duration;
import java.util.List;

public final class Main {
    public static void main(String[] args) {
        SimulationConfig config = SimulationConfig.sample();
        List<CustomerProfile> customers = List.of(
                new CustomerProfile("Alice", List.of("BUSINESS_LICENSE"), Duration.ofMillis(200)),
                new CustomerProfile("Bob", List.of("RESIDENCY_CERT"), Duration.ofMillis(500)),
                new CustomerProfile("Chloe", List.of("TAX_CLEARANCE", "HEALTH_INSURANCE"), Duration.ofMillis(300)),
                new CustomerProfile("Diego", List.of("CITIZEN_CARD"), Duration.ofMillis(1000)),
                new CustomerProfile("Elena", List.of("BUSINESS_LICENSE"), Duration.ofMillis(1200))
        );

        SimulationReporter reporter = new ConsoleSimulationReporter();
        try (BureaucracySimulation simulation = new BureaucracySimulation(config, customers, reporter)) {
            simulation.run();
        }
    }
}
