package behavioral


sealed class SyncChannelMessage {
    data class SyncPoint(
        val sender: RegisteredBThread,
        val request: Set<Event>,  // Events to request
        val waitFor: Set<Event>,     // Events to wait for
        val block: Set<Event>     // Events to block
    ) : SyncChannelMessage()

    data class Terminate (val sender: RegisteredBThread)
        : SyncChannelMessage()
}