package behavioral

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException


class CompletionException : CancellationException("Behavior completed")

class BProgram {
    private val bThreads = mutableListOf<RegisteredBThread>()
    private val jobToBThread = mutableMapOf<Job, RegisteredBThread>()
    private val syncPoints = mutableMapOf<RegisteredBThread, SyncPoint>()
    private val syncChannel = Channel<SyncPoint>(Channel.UNLIMITED)

    private var busyCount = AtomicInteger(0)

    private var debugMode = false
    fun debugMode() {
        debugMode = true
    }

    val debugPrint : (String) -> Unit by lazy {
        if (debugMode) ::println else { _ -> }
    }


    fun add(bThread: BThread, priority: Double) {
        val registeredBThread = RegisteredBThread(bThread.name, bThread.behavior, priority, syncChannel)
        bThreads.add(registeredBThread)
    }

    fun startAll() {
        try {
            runBlocking {
                start()
            }
        } catch (e: CompletionException) {
            debugPrint("Program completed")
        }
    }


    val start: suspend CoroutineScope.() -> Unit = {
        val jobs = bThreads.map { bThread ->
            val job = launch {
                bThread.start()
            }
            jobToBThread[job] = bThread
            job
        }

        jobs.forEach { job->
            job.invokeOnCompletion {
                val bThread = jobToBThread[job]!!
                syncPoints.remove(bThread)
                val x =  busyCount.addAndGet(-1)
                debugPrint("thread closed busyCount: $x")
            }
        }



        busyCount.addAndGet(bThreads.size)
        while (busyCount.get() > 0) {
            val message = syncChannel.receive()
            syncPoints[message.sender] = message

            val x= busyCount.addAndGet(-1)
            debugPrint("sync received busyCount: $x")

            if (busyCount.get() == 0) {
                val allRequests = syncPoints.values.flatMap { it.request }.toSet()
                //val allWaitFor = syncPoints.values.flatMap { it.waitFor }.toSet()
                val allBlock = syncPoints.values.flatMap { it.block }.toSet()

                val satisfied = allRequests - allBlock
                val unsatisfied = allRequests - satisfied

                val eventPriorities = mutableMapOf<Event, Double>()
                for (event in satisfied) {
                    val priorities = syncPoints.filter { (_, sp) ->
                        event in sp.request || event in sp.waitFor
                    }.map { (behavior, _) ->
                        bThreads.find { it.name == behavior.name }?.priority ?: 0.0
                    }
                    if (priorities.isNotEmpty()) {
                        eventPriorities[event] = priorities.maxOrNull() ?: 0.0
                    }
                }
                val selectedEvent = eventPriorities.minByOrNull { it.value }?.key

                val toNotify = syncPoints.filter { (_, sp) ->
                    selectedEvent in sp.request || selectedEvent in sp.waitFor
                }

                toNotify.toSortedMap(compareBy { it.priority }).forEach { (bThread, syncPoint) ->
                    if (selectedEvent != null) {
                        busyCount.addAndGet(1)
                        bThread.eventChannel.send(selectedEvent)
                    }
                }
            }
        }

        cancel(CompletionException())
    }

}
