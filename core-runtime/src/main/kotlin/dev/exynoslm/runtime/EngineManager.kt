package dev.exynoslm.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps at most [maxResident] engines loaded and unloads the least-recently-used one.
 * Call [trimAll] on onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL) or when the app is backgrounded.
 */
class EngineManager(private val maxResident: Int = 1) {
    private val mutex = Mutex()
    private val lru = LinkedHashMap<String, InferenceEngine>(4, 0.75f, true)

    suspend fun touch(key: String, engine: InferenceEngine) = mutex.withLock {
        lru[key] = engine
        while (lru.size > maxResident) {
            val eldest = lru.entries.first()
            lru.remove(eldest.key)
            eldest.value.unload()
        }
    }

    suspend fun trimAll() = mutex.withLock {
        lru.values.forEach { it.unload() }
        lru.clear()
    }

    suspend fun residentKeys(): List<String> = mutex.withLock { lru.keys.toList() }
}
