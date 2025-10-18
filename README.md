# Bureaucracy Concurrency Simulation

This project models a bustling bureaucratic system where customers chase paperwork across multiple public offices. It demonstrates coordination across queues, cross-office dependencies, and coffee break pauses using the Java Concurrency API.

## Features
- **Multi-counter offices** – each office runs several worker threads that process clients one at a time while honoring queue order.
- **Document dependencies** – obtaining a document can trigger additional document requests, even across offices, without deadlocks.
- **Coffee breaks** – offices periodically suspend service; ongoing transactions finish gracefully while new clients wait for the office to reopen.
- **Narrated timeline** – a console reporter chronicles system, office, and customer events for easy debugging and demos.

## Project Layout
```
src/main/java/com/bureaucracy/
├── Main.java                       # Entry point with sample scenario
├── config/SimulationConfig.java    # Immutable setup for offices & documents
└── simulation/
    ├── BureaucracySimulation.java  # Coordinates offices, customers, breaks
    ├── ConsoleSimulationReporter.java
    ├── CustomerProfile.java
    ├── CustomerSimulation.java
    ├── DocumentManager.java
    ├── DocumentProcessingResult.java
    ├── IssuanceTask.java
    ├── Office.java                 # Queue + break management logic
    ├── OfficeEventListener.java
    ├── OfficeState.java
    └── SimulationReporter.java
```

## Requirements
- Java 17 or newer (for `Duration` enhancements and convenience APIs).

## Building & Running
Compile everything into an `out` directory, then run the main class:
```bash
javac -d out $(find src/main/java -name "*.java")
java -cp out com.bureaucracy.Main
```

The bundled scenario:
- Initializes four offices with distinct service and break profiles.
- Launches five customers with staggered arrival times.
- Emits step-by-step log lines such as:
```
20:15:12 [SYS] Simulation starting with 5 customers and 4 offices
20:15:14 [CUS:Alice] received ID_FORM from Identity Office in 1150 ms
20:15:20 [OFF:City Hall] Coffee break requested
```

## Customizing the Simulation
- **Documents & Offices**: edit `SimulationConfig.sample()` to add or modify offices, counters, service times, break durations, and document dependency chains.
- **Customers**: adjust the list in `Main` (arrival delays and requested documents).
- **Break cadence**: tweak `scheduleNextBreak` inside `BureaucracySimulation` for different pause frequency.

## Notes
- Office workers inline any dependency work for their own office, avoiding re-queuing deadlocks.
- Scheduled coffee breaks wait for active services to finish before pausing threads.
- The slow timings (seconds) are intentional for readability; revert to faster `Duration` values when benchmarking.
