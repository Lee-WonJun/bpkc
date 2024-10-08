package behavioral

interface Event {}

object None : Set<Event> {
    override val size: Int
        get() = 0

    override fun contains(element: Event): Boolean {
        return false
    }

    override fun containsAll(elements: Collection<Event>): Boolean {
        return false
    }

    override fun isEmpty(): Boolean {
        return true
    }

    override fun iterator(): Iterator<Event> {
        return emptySet<Event>().iterator()
    }
}

object All : Set<Event> {
    override val size: Int
        get() = 0

    override fun contains(element: Event): Boolean {
        return true
    }

    override fun containsAll(elements: Collection<Event>): Boolean {
        return true
    }

    override fun isEmpty(): Boolean {
        return false
    }

    override fun iterator(): Iterator<Event> {
        return emptySet<Event>().iterator()
    }
}