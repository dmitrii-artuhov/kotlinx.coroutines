package kotlinx.coroutines.gpmc.internal

import gpmc.*
import junit.framework.Assert.*
import kotlinx.coroutines.internal.*
import org.junit.*
import kotlin.concurrent.*

class ConcurrentWeakMapCollectionModelCheckingTest : GPMCTestBase() {
    private data class Key(val i: Int)
    private val nElements = 5
    private val size = 100_000

    @Ignore("= Concurrent test has hung =")
    @Test
    fun testCollected() = runGPMCTest {
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