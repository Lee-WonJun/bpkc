package behavioral.sample

import behavioral.All
import behavioral.Event
import behavioral.None
import behavioral.bProgram
import behavioral.bThread
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual


enum class WaterEvent : Event {
    ADD_HOT, ADD_COLD
}


class WaterDrop : FunSpec({
    test("WaterDrop") {
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
            for (i in 1..3) { // Limit interleave to 5 times
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


        val assert = bThread(name = "Assert") {
            val hot_cold_loop = sequence {
                while(true) {
                    yield(WaterEvent.ADD_HOT)
                    yield(WaterEvent.ADD_COLD)
                }
            }

            for (i in hot_cold_loop) {
                sync(request = All)
                lastEvent shouldBeEqual i
            }
        }

        // Create the BProgram with all BThreads
        val program = bProgram(
            hotWater,
            coldWater,
            interleave,
            display,
            assert
        )

        program.enableDebug()
        program.runAllBThreads()

    }
})




