package com.bureaucracy;

import com.bureaucracy.config.SimulationConfig;
import com.bureaucracy.simulation.BureaucracySimulation;
import com.bureaucracy.simulation.ConsoleSimulationReporter;
import com.bureaucracy.simulation.CustomerProfile;

import java.time.Duration;
import java.util.List;

public final class Main {
    public static void main(String[] args) {
        SimulationConfig config = SimulationConfig.sample();
        List<CustomerProfile> customers = List.of(
                new CustomerProfile("Mihai", List.of("AVIZ_AFACERI"), Duration.ofMillis(0)),
                new CustomerProfile("Ioana", List.of("ADEVERINTA_DOMICILIU"), Duration.ofMillis(0)),
                new CustomerProfile("Andrei", List.of("CERTIFICAT_FISCAL", "CARD_SANATATE"), Duration.ofMillis(0)),
                new CustomerProfile("Sorina", List.of("CARTE_IDENTITATE"), Duration.ofMillis(0)),
                new CustomerProfile("Vlad", List.of("AVIZ_AFACERI"), Duration.ofMillis(0))
        );

        try (ConsoleSimulationReporter reporter = new ConsoleSimulationReporter();
             BureaucracySimulation simulation = new BureaucracySimulation(config, customers, reporter)) {
            simulation.run();
        }
    }
}
