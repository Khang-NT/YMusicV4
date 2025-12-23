package kmpcommon

/**
 * A doubly-linked list implementation extending AbstractMutableList for Kotlin Multiplatform.
 */
class LinkedList<T> : AbstractMutableList<T>() {

    private class Node<T>(
        var value: T,
        var prev: Node<T>? = null,
        var next: Node<T>? = null
    )

    private var head: Node<T>? = null
    private var tail: Node<T>? = null
    private var _size: Int = 0

    override val size: Int get() = _size

    // ==================== Core Abstract Methods ====================

    override fun get(index: Int): T {
        checkElementIndex(index)
        return nodeAt(index).value
    }

    override fun set(index: Int, element: T): T {
        checkElementIndex(index)
        val node = nodeAt(index)
        val oldValue = node.value
        node.value = element
        return oldValue
    }

    override fun add(index: Int, element: T) {
        checkPositionIndex(index)
        when (index) {
            0 -> addFirst(element)
            _size -> addLast(element)
            else -> {
                val current = nodeAt(index)
                val newNode = Node(element, prev = current.prev, next = current)
                current.prev?.next = newNode
                current.prev = newNode
                _size++
            }
        }
    }

    override fun removeAt(index: Int): T {
        checkElementIndex(index)
        val node = nodeAt(index)
        unlink(node)
        return node.value
    }

    // ==================== Optimized Overrides ====================

    override fun add(element: T): Boolean {
        addLast(element)
        return true
    }

    override fun clear() {
        head = null
        tail = null
        _size = 0
    }

    override fun iterator(): MutableIterator<T> = listIterator(0)

    override fun listIterator(): MutableListIterator<T> = listIterator(0)

    override fun listIterator(index: Int): MutableListIterator<T> {
        checkPositionIndex(index)
        return LinkedListIterator(index)
    }

    // ==================== Deque-style Operations ====================

    fun addFirst(element: T) {
        val newNode = Node(element, next = head)
        head?.prev = newNode
        head = newNode
        if (tail == null) tail = newNode
        _size++
    }

    fun addLast(element: T) {
        val newNode = Node(element, prev = tail)
        tail?.next = newNode
        tail = newNode
        if (head == null) head = newNode
        _size++
    }

    fun removeFirst(): T {
        if (head == null) throw NoSuchElementException("List is empty")
        val value = head!!.value
        unlink(head!!)
        return value
    }

    fun removeLast(): T {
        if (tail == null) throw NoSuchElementException("List is empty")
        val value = tail!!.value
        unlink(tail!!)
        return value
    }

    fun first(): T = head?.value ?: throw NoSuchElementException("List is empty")

    fun last(): T = tail?.value ?: throw NoSuchElementException("List is empty")

    fun firstOrNull(): T? = head?.value

    fun lastOrNull(): T? = tail?.value

    // ==================== Iterator Implementation ====================

    private inner class LinkedListIterator(private var nextIndex: Int) : MutableListIterator<T> {
        private var lastReturned: Node<T>? = null
        private var nextNode: Node<T>? = when {
            nextIndex == _size -> null
            nextIndex <= _size / 2 -> nodeFromHead(nextIndex)
            else -> nodeFromTail(nextIndex)
        }

        override fun hasNext(): Boolean = nextIndex < _size

        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            lastReturned = nextNode
            nextNode = nextNode?.next
            nextIndex++
            return lastReturned!!.value
        }

        override fun hasPrevious(): Boolean = nextIndex > 0

        override fun previous(): T {
            if (!hasPrevious()) throw NoSuchElementException()
            nextNode = nextNode?.prev ?: tail
            lastReturned = nextNode
            nextIndex--
            return lastReturned!!.value
        }

        override fun nextIndex(): Int = nextIndex

        override fun previousIndex(): Int = nextIndex - 1

        override fun remove() {
            val node = lastReturned ?: throw IllegalStateException("Call next() or previous() first")
            if (nextNode == node) {
                nextNode = node.next
            } else {
                nextIndex--
            }
            unlink(node)
            lastReturned = null
        }

        override fun set(element: T) {
            val node = lastReturned ?: throw IllegalStateException("Call next() or previous() first")
            node.value = element
        }

        override fun add(element: T) {
            when {
                nextNode == null -> addLast(element)
                nextNode == head -> addFirst(element)
                else -> {
                    val newNode = Node(element, prev = nextNode?.prev, next = nextNode)
                    nextNode?.prev?.next = newNode
                    nextNode?.prev = newNode
                    _size++
                }
            }
            nextIndex++
            lastReturned = null
        }
    }

    // ==================== Internal Utility ====================

    private fun nodeAt(index: Int): Node<T> {
        return if (index <= _size / 2) {
            nodeFromHead(index)
        } else {
            nodeFromTail(index)
        }
    }

    private fun nodeFromHead(index: Int): Node<T> {
        var current = head
        repeat(index) { current = current?.next }
        return current!!
    }

    private fun nodeFromTail(index: Int): Node<T> {
        var current = tail
        repeat(_size - 1 - index) { current = current?.prev }
        return current!!
    }

    private fun unlink(node: Node<T>) {
        val prev = node.prev
        val next = node.next

        if (prev == null) {
            head = next
        } else {
            prev.next = next
            node.prev = null
        }

        if (next == null) {
            tail = prev
        } else {
            next.prev = prev
            node.next = null
        }

        _size--
    }

    private fun checkElementIndex(index: Int) {
        if (index < 0 || index >= _size) {
            throw IndexOutOfBoundsException("Index: $index, Size: $_size")
        }
    }

    private fun checkPositionIndex(index: Int) {
        if (index < 0 || index > _size) {
            throw IndexOutOfBoundsException("Index: $index, Size: $_size")
        }
    }
}

// ==================== Factory Functions ====================

fun <T> linkedListOf(vararg elements: T): LinkedList<T> {
    return LinkedList<T>().apply {
        elements.forEach { add(it) }
    }
}

fun <T> Collection<T>.toLinkedList(): LinkedList<T> {
    return LinkedList<T>().apply { addAll(this@toLinkedList) }
}