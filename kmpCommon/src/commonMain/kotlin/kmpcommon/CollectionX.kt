package kmpcommon

inline fun <K, V> MutableMap<K, V>.removeAll(predicate: (Map.Entry<K, V>) -> Boolean): MutableMap<K, V> {
    val iterator = iterator()
    while (iterator.hasNext()) {
        val next = iterator.next()
        if (predicate(next)) {
            iterator.remove()
        }
    }
    return this
}