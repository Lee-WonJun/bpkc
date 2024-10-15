# BPKC (Behavioral Programming for Kotlin through Channels)

BPKC is a lightweight approach to Behavioral Programming, using Kotlin’s Channel system and Coroutines to define and manage behaviors in a concurrent environment. It allows developers to model system behaviors and manage asynchronous tasks in a straightforward way.

## Behavioral Programming Concepts

Behavioral Programming models systems by dividing them into individual "behaviors" that interact with each other. Each behavior operates independently and responds to specific events in the system.

- **BThread**: Short for "Behavior Thread", it represents a unit of behavior. Each BThread runs its own flow and reacts to certain events in the system.
- **sync**: A method used by BThreads to coordinate with other events. It can request, wait for, or block certain events, allowing behaviors to collaborate without conflicts.

## Key Features

### 1. Lightweight Library
BPKC is a lightweight library designed for easy use of Behavioral Programming concepts. It simplifies the handling of asynchronous systems and parallel tasks.

### 2. Built on Kotlin's Channel and Coroutines
BPKC is implemented using Kotlin’s native primitives: Channels for communication and Coroutines for asynchronous execution. This ensures non-blocking code and seamless interaction between behaviors.

### 3. Kotlin-Friendly DSL (Domain-Specific Language)
BPKC is designed with a Kotlin-specific DSL, making it easy to write and understand. Developers can define and manage behaviors using concise and intuitive syntax, such as `bThread` and `sync`, which follow Kotlin’s idiomatic style.

### 4. Cooperation and Conflict Prevention Between Behaviors
Using the `sync` method, BPKC allows BThreads to coordinate with each other. They can request or wait for events, and block conflicting events to ensure smooth collaboration between behaviors.

## Constraints

1. **Designed for Educational and Learning Purposes**  
   BPKC is primarily designed for learning and demonstrating the basics of Behavioral Programming. It is not optimized for production environments.

2. **No Dynamic BThread Addition**  
   The current version of BPKC does not support adding or removing `bThreads` dynamically during execution. All behaviors must be defined at the start of the program.

3. **Limited Testing for Various Use Cases and Priorities**  
   BPKC has not undergone extensive testing across different real-world scenarios. Particularly, complex scenarios involving behavior priorities and conflict resolution require more in-depth testing.

## Sample Code

```kotlin
enum class WaterEvent : Event {
    ADD_HOT, ADD_COLD
}

// Define the Hot Water BThread
val hotWater = bThread(name = "Hot Water") {
    for (i in 1..3) {
        sync(request = setOf(WaterEvent.ADD_HOT), waitFor = None, blockEvent = None)
    }
}

// Define the Cold Water BThread
val coldWater = bThread(name = "Cold Water") {
    for (i in 1..3) {
        sync(request = setOf(WaterEvent.ADD_COLD))
    }
}

// Define the Interleave BThread
val interleave = bThread(name = "Interleave") {
    for (i in 1..3) { 
        sync(waitFor = setOf(WaterEvent.ADD_HOT), blockEvent = setOf(WaterEvent.ADD_COLD))
        sync(waitFor = setOf(WaterEvent.ADD_COLD), blockEvent = setOf(WaterEvent.ADD_HOT))
    }
}

// Define the Display BThread
val display = bThread(name = "Display") {
    while(true) {
        sync(waitFor = All)
        println("[${this.name}] turned water tap: $lastEvent")
    }
}

// Create and run the BProgram
val program = bProgram(
    hotWater,
    coldWater,
    interleave,
    display
)

program.enableDebug()
program.runAllBThreads()
```

In this example, the `Hot Water` and `Cold Water` behaviors alternate execution three times each, controlled by the `Interleave` BThread. The `Display` BThread prints the current event each time it occurs.

## References

- [Behavioral Programming Research](https://cacm.acm.org/research/behavioral-programming/)
- [BPJ Library](https://wiki.weizmann.ac.il/bp/index.php?title=The_BPJ_Library)
- [Introduction to Behavioral Programming Video](https://www.youtube.com/watch?v=1oKzTrq0gMM)
- [Behavioral Programming in Clojure](https://thomascothran.tech/2024/09/in-clojure/)
- [Crash Course on Behavioral Programming](https://medium.com/@eugenesh4work/crash-course-behavioral-programming-in-clojure-with-core-async-07ed06ddd760)
- [Behavioral Programming written in Kotlin and optimized for Android](https://github.com/EricDw/BPK-4-DROID)
- [Behavioral Programming for JavaScript](https://github.com/lmatteis/behavioral)

BPKC is a useful tool for modeling asynchronous tasks and complex behavior interactions. However, as it is primarily designed for educational purposes, it lacks dynamic behavior management and needs more testing for advanced scenarios.
