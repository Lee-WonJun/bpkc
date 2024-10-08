package behavioral


data class SyncPoint(
    val sender: RegisteredBThread,
    val request: Set<Event>,  // Events to request
    val waitFor: Set<Event>,     // Events to wait for
    val block: Set<Event>     // Events to block
)