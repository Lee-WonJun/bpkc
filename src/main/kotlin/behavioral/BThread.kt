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
    val syncChannel: Channel<SyncPoint>
) {
    val eventChannel = Channel<String>(Channel.UNLIMITED)
    var lastEvent: String = ""


    suspend fun sync(
        request: Set<Event> = None,
        waitFor: Set<Event> = None,
        blockEvent: Set<Event> = None
    ) {
        val syncPoint = SyncPoint(
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
    }
}