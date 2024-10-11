package behavioral

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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
    private val syncPoints = ConcurrentHashMap<RegisteredBThread, SyncChannelMessage.SyncPoint>()
    private val syncChannel = Channel<SyncChannelMessage>(Channel.UNLIMITED)
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
        when (val syncPoint = syncChannel.receive()) {
            is SyncChannelMessage.SyncPoint -> {
                syncPoints[syncPoint.sender] = syncPoint
                activeThreads.decrementAndGet().also { debugLog("activeThreads: $it") }

                if (needsSync) {
                    val nextEvent = selectNextEvent()

                    val threadsToNotify = determineThreadsToNotify(nextEvent)

                    debugLog("Next event: $nextEvent")
                    debugLog("Threads to notify: ${threadsToNotify.keys.joinToString { it.name }}")
                    notifyBThreads(threadsToNotify, nextEvent)
                }
            }
            is SyncChannelMessage.Terminate -> {
                syncPoints.remove(syncPoint.sender)
                activeThreads.decrementAndGet().also {
                    debugLog("terminated ${syncPoint.sender.name}")
                }
            }
        }
    }

    private suspend fun notifyBThreads(
        threadsToNotify: Map<RegisteredBThread, SyncChannelMessage.SyncPoint>,
        nextEvent: Event?
    ) {
        nextEvent?.let { event ->
            threadsToNotify.toSortedMap(compareBy { -it.priority })
                .also { bThreads -> debugLog("Sorted threads: ${bThreads.keys.joinToString { it.name + it.priority.toString() }}") }
                .forEach { (bThread, _) ->
                    activeThreads.incrementAndGet()
                    debugLog("[Notify ${bThread.name}] to send $event")
                    bThread.eventChannel.send(event)
                }
        }
    }

    private fun determineThreadsToNotify(nextEvent: Event?): Map<RegisteredBThread, SyncChannelMessage.SyncPoint> {
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
            }
        }
    }
}
