package com.bureaucracy.simulation;

import com.bureaucracy.config.SimulationConfig;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Models an office with one or more counters that process incoming clients sequentially,
 * while supporting temporary pauses (coffee breaks) and providing status inspection.
 *
 * The implementation purposefully sticks to classic concurrency primitives (synchronized,
 * wait/notify, latches) to keep the flow approachable and easy to reason about.
 */
public class Office {

    private final String name;
    private final SimulationConfig.OfficeConfig config;
    private final SimulationReporter reporter;

    private final Deque<OfficeQueueEntry> queue = new ArrayDeque<>();
    private final List<Thread> counters = new ArrayList<>();
    private final AtomicInteger taskSequence = new AtomicInteger(0);
    private final Object monitor = new Object();
    private final Semaphore breakSemaphore = new Semaphore(1, true);

    private boolean accepting = true;
    private boolean breakRequested = false;
    private boolean onBreak = false;
    private boolean shutdown = false;
    private int activeServices = 0;
    private CountDownLatch breakLatch;

    public Office(SimulationConfig.OfficeConfig config, SimulationReporter reporter) {
        this.name = Objects.requireNonNull(config, "config").getName();
        this.config = config;
        this.reporter = reporter;
        startCounters();
        log("Office opened with " + config.getCounters() + " counters");
    }

    public String getName() {
        return name;
    }

    public OfficeState getState() {
        synchronized (monitor) {
            if (shutdown) {
                return OfficeState.SHUTDOWN;
            }
            if (onBreak) {
                return OfficeState.ON_BREAK;
            }
            if (breakRequested || !accepting) {
                return OfficeState.BREAK_PENDING;
            }
            return OfficeState.OPEN;
        }
    }

    public int queueSize() {
        synchronized (monitor) {
            return queue.size();
        }
    }

    public CompletableFuture<DocumentProcessingResult> submit(IssuanceTask task) throws InterruptedException {
        Objects.requireNonNull(task, "task");

        synchronized (monitor) {
            while (!accepting && !shutdown) {
                monitor.wait();
            }
            if (shutdown) {
                throw new IllegalStateException("Office " + name + " is shutting down");
            }

            OfficeQueueEntry entry = new OfficeQueueEntry(task, taskSequence.incrementAndGet());
            queue.addLast(entry);
            monitor.notifyAll();
            reporter.queueEvent(name, task.getCustomerId(), task.getDocumentName(), snapshotQueue());
            return entry.completion;
        }
    }

    public void takeCoffeeBreak() throws InterruptedException {
        if (config.getBreakDuration().isZero()) {
            return;
        }
        breakSemaphore.acquire();
        try {
            beginBreak();
            log("Coffee break started for " + config.getBreakDuration().toSeconds() + " seconds");
            Thread.sleep(config.getBreakDuration().toMillis());
        } finally {
            endBreak();
            breakSemaphore.release();
        }
    }

    public void shutdown() {
        synchronized (monitor) {
            if (shutdown) {
                return;
            }
            shutdown = true;
            monitor.notifyAll();
        }

        for (Thread counter : counters) {
            counter.interrupt();
        }
        for (Thread counter : counters) {
            boolean joined = false;
            while (!joined) {
                try {
                    counter.join();
                    joined = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log("Office closed");
    }

    private void beginBreak() throws InterruptedException {
        CountDownLatch latchToAwait = null;
        synchronized (monitor) {
            if (shutdown) {
                return;
            }
            if (breakRequested || onBreak) {
                while (!onBreak && !shutdown) {
                    monitor.wait();
                }
                return;
            }
            accepting = false;
            breakRequested = true;
            log("Coffee break requested");
            if (activeServices == 0) {
                enterBreakModeLocked();
            } else {
                log("Waiting for " + activeServices + " active service(s) to finish before break");
                breakLatch = new CountDownLatch(activeServices);
                latchToAwait = breakLatch;
            }
        }
        if (latchToAwait != null) {
            latchToAwait.await();
            synchronized (monitor) {
                enterBreakModeLocked();
            }
        }
    }

    private void endBreak() {
        synchronized (monitor) {
            if (shutdown) {
                return;
            }
            onBreak = false;
            accepting = true;
            log("Coffee break ended, office is now OPEN");
            monitor.notifyAll();
        }
    }

    private void enterBreakModeLocked() {
        onBreak = true;
        breakRequested = false;
        breakLatch = null;
        log("Office is now ON_BREAK");
        monitor.notifyAll();
    }

    private void startCounters() {
        for (int i = 0; i < config.getCounters(); i++) {
            int counterIndex = i;
            Thread counterThread = new Thread(() -> counterLoop(counterIndex), name + "-counter-" + counterIndex);
            counterThread.start();
            counters.add(counterThread);
        }
    }

    private void counterLoop(int counterIndex) {
        try {
            while (true) {
                OfficeQueueEntry entry;
                synchronized (monitor) {
                    while (!shutdown && (queue.isEmpty() || onBreak || breakRequested)) {
                        if (shutdown) {
                            return;
                        }
                        try {
                            monitor.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    if (shutdown) {
                        return;
                    }
                    entry = queue.removeFirst();
                    activeServices++;
                    reporter.arrivalEvent(name, counterIndex, entry.task.getCustomerId(), entry.task.getDocumentName());
                }

                try {
                    DocumentProcessingResult result = executeTask(entry.task);
                    entry.completion.complete(result);
                    reporter.finishEvent(name, counterIndex, entry.task.getCustomerId(), entry.task.getDocumentName());
                    log("Completed task #" + entry.sequence + " for " + entry.task.getCustomerId() +
                            " (" + entry.task.getDocumentName() + ") in " + result.getServiceDuration().toMillis() + " ms");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    entry.completion.completeExceptionally(e);
                    return;
                } catch (Exception e) {
                    entry.completion.completeExceptionally(e);
                } finally {
                    synchronized (monitor) {
                        activeServices--;
                        if (breakLatch != null) {
                            breakLatch.countDown();
                        }
                        monitor.notifyAll();
                    }
                }
            }
        } finally {
            // nothing to clean up
        }
    }

    private DocumentProcessingResult executeTask(IssuanceTask task) throws Exception {
        long start = System.nanoTime();
        simulateServiceDelay();
        DocumentProcessingResult result = task.getWork().call();
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        return result.withServiceDuration(duration);
    }

    private void simulateServiceDelay() throws InterruptedException {
        Duration min = config.getMinServiceTime();
        Duration max = config.getMaxServiceTime();
        long minMillis = min.toMillis();
        long maxMillis = Math.max(minMillis, max.toMillis());
        long delay = ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
        Thread.sleep(delay);
    }

    private void log(String message) {
        reporter.onEvent(name, message);
    }

    private List<String> snapshotQueue() {
        List<String> snapshot = new ArrayList<>();
        for (OfficeQueueEntry entry : queue) {
            snapshot.add("person " + entry.task.getCustomerId() + " REQUESTING " + entry.task.getDocumentName());
        }
        return snapshot;
    }

    private static final class OfficeQueueEntry {
        private final IssuanceTask task;
        private final int sequence;
        private final CompletableFuture<DocumentProcessingResult> completion = new CompletableFuture<>();

        private OfficeQueueEntry(IssuanceTask task, int sequence) {
            this.task = task;
            this.sequence = sequence;
        }
    }

}
