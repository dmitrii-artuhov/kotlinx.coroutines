package kotlinx.coroutines.gpmc.internal

import junit.framework.Assert.*
import kotlinx.coroutines.internal.*
import org.jetbrains.kotlinx.lincheck.*
import org.junit.*
import kotlin.concurrent.*

@Ignore("= Concurrent test has hung =")
class ConcurrentWeakMapCollectionModelCheckingTest {
    private data class Key(val i: Int)
    private val nElements = 5
    private val size = 100_000
    
    @OptIn(ExperimentalModelCheckingAPI::class)
    @Test
    fun testCollected() {
        runConcurrentTest {
            // use very big arrays as values, we'll need a queue and a cleaner thread to handle them
            val m = ConcurrentWeakMap<Key, ByteArray>(weakRefQueue = true)
            val cleaner = thread(name = "ConcurrentWeakMapCollectionStressTest-Cleaner") {
                m.runWeakRefQueueCleaningLoopUntilInterrupted()
            }
            for (i in 1..nElements) {
                m.put(Key(i), ByteArray(size))
            }
            assertTrue(m.size <= nElements)
            cleaner.interrupt()
            cleaner.join()
        }
    }
}