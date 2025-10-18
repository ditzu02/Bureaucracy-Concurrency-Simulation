package com.bureaucracy.simulation;

import com.bureaucracy.config.SimulationConfig;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Models an office with one or more counters that process incoming clients sequentially,
 * while supporting temporary pauses (coffee breaks) and providing status inspection.
 */
public class Office {

    private final String name;
    private final SimulationConfig.OfficeConfig config;
    private final OfficeEventListener eventListener;

    private static final ThreadLocal<Office> COUNTER_CONTEXT = new ThreadLocal<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition queueAvailable = lock.newCondition();
    private final Condition stateChanged = lock.newCondition();
    private final Deque<OfficeQueueEntry> queue = new ArrayDeque<>();
    private final List<Thread> counters = new ArrayList<>();
    private final AtomicInteger taskSequence = new AtomicInteger(0);

    private boolean accepting = true;
    private boolean breakRequested = false;
    private boolean onBreak = false;
    private boolean shutdown = false;
    private int activeServices = 0;

    public Office(SimulationConfig.OfficeConfig config, OfficeEventListener eventListener) {
        this.name = Objects.requireNonNull(config, "config").getName();
        this.config = config;
        this.eventListener = eventListener != null ? eventListener : (office, message) -> {
        };
        startCounters();
        log("Office opened with " + config.getCounters() + " counters");
    }

    public String getName() {
        return name;
    }

    public OfficeState getState() {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public int queueSize() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    public CompletableFuture<DocumentProcessingResult> submit(IssuanceTask task) throws InterruptedException {
        Objects.requireNonNull(task, "task");

        if (COUNTER_CONTEXT.get() == this) {
            return processInline(task);
        }

        lock.lockInterruptibly();
        try {
            while (!shutdown && !accepting) {
                stateChanged.await();
            }
            if (shutdown) {
                throw new IllegalStateException("Office " + name + " is shutting down");
            }

            OfficeQueueEntry entry = new OfficeQueueEntry(task, taskSequence.incrementAndGet());
            queue.addLast(entry);
            queueAvailable.signal();
            log("Queued task #" + entry.sequence + " for " + task.getCustomerId() + " requesting " + task.getDocumentName() +
                    " (queue size=" + queue.size() + ")");
            return entry.completion;
        } finally {
            lock.unlock();
        }
    }

    public void takeCoffeeBreak() throws InterruptedException {
        if (config.getBreakDuration().isZero()) {
            return;
        }
        beginBreak();
        try {
            log("Coffee break started for " + config.getBreakDuration().toSeconds() + " seconds");
            Thread.sleep(config.getBreakDuration().toMillis());
        } finally {
            endBreak();
        }
    }

    public void shutdown() {
        lock.lock();
        try {
            if (shutdown) {
                return;
            }
            shutdown = true;
            queueAvailable.signalAll();
            stateChanged.signalAll();
        } finally {
            lock.unlock();
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
        lock.lockInterruptibly();
        try {
            if (shutdown) {
                return;
            }
            if (!accepting && (breakRequested || onBreak)) {
                while (!onBreak && !shutdown) {
                    stateChanged.await();
                }
                return;
            }
            accepting = false;
            breakRequested = true;
            log("Coffee break requested");
            queueAvailable.signalAll();
            if (activeServices == 0 && queue.isEmpty()) {
                enterBreakMode();
            }
            while (!onBreak && !shutdown) {
                stateChanged.await();
            }
        } finally {
            lock.unlock();
        }
    }

    private void endBreak() {
        lock.lock();
        try {
            if (shutdown) {
                return;
            }
            onBreak = false;
            accepting = true;
            log("Coffee break ended, office is now OPEN");
            queueAvailable.signalAll();
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void enterBreakMode() {
        onBreak = true;
        breakRequested = false;
        log("Office is now ON_BREAK");
        stateChanged.signalAll();
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
        COUNTER_CONTEXT.set(this);
        try {
            while (true) {
                OfficeQueueEntry entry;
                lock.lock();
                try {
                    while (!shutdown && (queue.isEmpty() || onBreak)) {
                        if (shutdown) {
                            return;
                        }
                        if (breakRequested && activeServices == 0 && queue.isEmpty()) {
                            enterBreakMode();
                        }
                        try {
                            queueAvailable.await(200, TimeUnit.MILLISECONDS);
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
                } finally {
                    lock.unlock();
                }

                try {
                    DocumentProcessingResult result = executeTask(entry.task);
                    entry.completion.complete(result);
                    log("Completed task #" + entry.sequence + " for " + entry.task.getCustomerId() +
                            " (" + entry.task.getDocumentName() + ") in " + result.getServiceDuration().toMillis() + " ms");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    entry.completion.completeExceptionally(e);
                    return;
                } catch (Exception e) {
                    entry.completion.completeExceptionally(e);
                } finally {
                    lock.lock();
                    try {
                        activeServices--;
                        if (breakRequested && activeServices == 0 && queue.isEmpty()) {
                            enterBreakMode();
                        } else {
                            queueAvailable.signal();
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            }
        } finally {
            COUNTER_CONTEXT.remove();
        }
    }

    private CompletableFuture<DocumentProcessingResult> processInline(IssuanceTask task) {
        CompletableFuture<DocumentProcessingResult> future = new CompletableFuture<>();
        try {
            DocumentProcessingResult result = executeTask(task);
            log("Completed inline task for " + task.getCustomerId() +
                    " (" + task.getDocumentName() + ") in " + result.getServiceDuration().toMillis() + " ms");
            future.complete(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
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
        eventListener.onEvent(name, message);
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
