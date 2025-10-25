package com.bureaucracy.simulation;

import com.bureaucracy.config.SimulationConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates offices, customers, and scheduled coffee breaks to run the simulation.
 */
public final class BureaucracySimulation implements AutoCloseable {

    private final SimulationConfig config;
    private final List<CustomerProfile> customers;
    private final SimulationReporter reporter;
    private final Map<String, Office> offices = new HashMap<>();
    private final ScheduledExecutorService breakScheduler;
    private final List<Thread> customerThreads = new ArrayList<>();
    private final DocumentManager documentManager;

    public BureaucracySimulation(SimulationConfig config, List<CustomerProfile> customers, SimulationReporter reporter) {
        this.config = Objects.requireNonNull(config, "config");
        this.customers = List.copyOf(Objects.requireNonNull(customers, "customers"));
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        for (SimulationConfig.OfficeConfig officeConfig : config.getOffices()) {
            offices.put(officeConfig.getName(), new Office(officeConfig, reporter));
        }
        this.documentManager = new DocumentManager(config, offices, reporter);
        this.breakScheduler = Executors.newScheduledThreadPool(Math.max(1, offices.size()));
    }

    public void run() {
        announceScenario();
        scheduleCoffeeBreaks();
        startCustomers();
        waitForCustomers();
        reporter.systemEvent("All customers finished");
    }

    private void scheduleCoffeeBreaks() {
        for (Office office : offices.values()) {
            scheduleNextBreak(office);
        }
    }

    private void scheduleNextBreak(Office office) {
        Duration minDelay = Duration.ofSeconds(6);
        Duration maxDelay = Duration.ofSeconds(10);
        long delay = ThreadLocalRandom.current().nextLong(minDelay.toMillis(), maxDelay.toMillis());
        breakScheduler.schedule(() -> {
            try {
                office.takeCoffeeBreak();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!breakScheduler.isShutdown()) {
                scheduleNextBreak(office);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void startCustomers() {
        for (CustomerProfile profile : customers) {
            Thread thread = new Thread(() -> {
                try {
                    if (!profile.getArrivalDelay().isZero()) {
                        Thread.sleep(profile.getArrivalDelay().toMillis());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                new CustomerSimulation(profile, documentManager, reporter).run();
            }, "customer-" + profile.getCustomerId());
            thread.start();
            customerThreads.add(thread);
        }
    }

    private void waitForCustomers() {
        for (Thread thread : customerThreads) {
            boolean done = false;
            while (!done) {
                try {
                    thread.join();
                    done = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public void close() {
        breakScheduler.shutdownNow();
        for (Office office : offices.values()) {
            office.shutdown();
        }
    }

    private void announceScenario() {
        reporter.systemEvent("Simulation starting with " + customers.size() + " customers and " + offices.size() + " offices");
        reporter.systemEvent("Offices in play:");
        for (SimulationConfig.OfficeConfig officeConfig : config.getOffices()) {
            String line = "- %s | counters=%d | service=%d-%d ms | break=%ds".formatted(
                    officeConfig.getName(),
                    officeConfig.getCounters(),
                    officeConfig.getMinServiceTime().toMillis(),
                    officeConfig.getMaxServiceTime().toMillis(),
                    officeConfig.getBreakDuration().toSeconds()
            );
            reporter.systemEvent(line);
        }
        reporter.systemEvent("Customers queued:");
        for (CustomerProfile customer : customers) {
            String line = "- %s arrives after %d ms requesting %s".formatted(
                    customer.getCustomerId(),
                    customer.getArrivalDelay().toMillis(),
                    String.join(", ", customer.getRequestedDocuments())
            );
            reporter.systemEvent(line);
        }
    }
}
