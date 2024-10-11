package behavioral

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

object BThreadContextKey : CoroutineContext.Key<BThreadContext>
data class BThreadContext(val bThread: RegisteredBThread) : AbstractCoroutineContextElement(BThreadContextKey)

class BCompletionException : CancellationException("Behavior completed")

class BProgram {
    private val bThreads = mutableListOf<RegisteredBThread>()
    private val syncPoints = ConcurrentHashMap<RegisteredBThread, SyncPoint>()
    private val syncChannel = Channel<SyncPoint>(Channel.UNLIMITED)
    private val activeThreads = AtomicInteger(0)

    private var debugEnabled = false
    fun enableDebug() {
        debugEnabled = true
    }

    val debugLog: (String) -> Unit by lazy {
        if (debugEnabled) ::println else { _ -> }
    }

    fun registerBThread(bThread: BThread, priority: Double) {
        val registeredBThread = RegisteredBThread(
            bThread.name,
            bThread.behavior,
            priority,
            syncChannel
        )
        bThreads.add(registeredBThread)
    }

    fun runAllBThreads() {
        try {
            runBlocking {
                executeBProgram()
            }
        } catch (e: BCompletionException) {
            debugLog("Program completed")
        }
    }

    val executeBProgram: suspend CoroutineScope.() -> Unit = {
        initializeBThreads()

        while (isActive) {
            handleSyncPoint()
        }

        cancel(BCompletionException())
    }

    private val isActive get() = activeThreads.get() > 0
    private val needsSync get() = activeThreads.get() == 0

    private suspend fun handleSyncPoint() {
        val syncPoint = syncChannel.receive()
        syncPoints[syncPoint.sender] = syncPoint
        activeThreads.decrementAndGet().also { debugLog("activeThreads: $it") }

        if (needsSync) {
            val nextEvent = selectNextEvent() ?: return

            val threadsToNotify = determineThreadsToNotify(nextEvent)

            notifyBThreads(threadsToNotify, nextEvent)
        }
    }

    private suspend fun notifyBThreads(
        threadsToNotify: Map<RegisteredBThread, SyncPoint>,
        nextEvent: Event?
    ) {
        threadsToNotify.toSortedMap(compareBy { it.priority }).forEach { (bThread, _) ->
            nextEvent?.let {
                activeThreads.incrementAndGet()
                debugLog("[Notify ${bThread.name}] to send $it")
                bThread.eventChannel.send(it)
            }
        }
    }

    private fun determineThreadsToNotify(nextEvent: Event?): Map<RegisteredBThread, SyncPoint> {
        return syncPoints.filter { (_, syncPoint) ->
            nextEvent in syncPoint.request || nextEvent in syncPoint.waitFor
        }
    }

    private fun selectNextEvent(): Event? {
        val requestedEvents = syncPoints.values.flatMap { it.request }.toSet()
        val blockedEvents = syncPoints.values.flatMap { it.block }.toSet()
        val availableEvents = requestedEvents - blockedEvents

        return availableEvents.maxByOrNull {
            val p = syncPoints.filter { (_, syncPoint) ->
                it in syncPoint.request || it in syncPoint.waitFor
            }.mapNotNull { (bThread, _) ->
                bThread.priority
            }
            p.maxOrNull() ?: 0.0
        }
    }

    private fun CoroutineScope.initializeBThreads() {
        activeThreads.set(bThreads.size)

        bThreads.forEach { bThread ->
            launch(BThreadContext(bThread)) {
                bThread.start()
            }.also { job ->
                job.invokeOnCompletion {
                    val context = (job as? CoroutineScope)?.coroutineContext?.get(BThreadContextKey)

                    context?.bThread?.let { removedThread ->
                        syncPoints.remove(removedThread)
                        val active = activeThreads.decrementAndGet()
                            .also { debugLog("[Job Completed ${removedThread.name}] activeThreads: $it") }
                    }
                }
            }
        }
    }
}
