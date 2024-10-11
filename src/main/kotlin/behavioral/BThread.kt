package behavioral

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.yield

class BThread(
    val name: String,
    val behavior: suspend RegisteredBThread.() -> Unit
)

class RegisteredBThread(
    val name: String,
    val behavior: suspend RegisteredBThread.() -> Unit,
    val priority: Double,
    private val syncChannel: Channel<SyncChannelMessage>
) {
    val eventChannel = Channel<Event>(Channel.UNLIMITED)
    lateinit var lastEvent: Event


    suspend fun sync(
        request: Set<Event> = None,
        waitFor: Set<Event> = None,
        blockEvent: Set<Event> = None
    ) {
        val syncPoint = SyncChannelMessage.SyncPoint(
            this,
            request,
            waitFor,
            blockEvent
        )
        syncChannel.send(syncPoint)
        // Wait for the event to be sent back
        val event = eventChannel.receive()
        lastEvent = event
        yield()
    }

    suspend fun start() {
        behavior()
        terminate()
    }

    suspend fun terminate() {
        syncChannel.send(SyncChannelMessage.Terminate(this))
    }
}