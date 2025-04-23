package kotlinx.coroutines.gpmc.issues

import gpmc.*
import kotlinx.coroutines.*
import org.junit.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class Issue3654 : GPMCTestBase() {

    private val inDefault = AtomicInteger(0)

    private val POOL_SIZE = 3

    private fun CountDownLatch.runAndCheck() {
        if (inDefault.incrementAndGet() > POOL_SIZE) {
            error("Oversubscription detected")
        }

        await()
        inDefault.decrementAndGet()
    }

    @Test
    fun testOverSubscriptionModelChecking() = runGPMCTest(10000) {
        val pool = Executors.newFixedThreadPool(POOL_SIZE).asCoroutineDispatcher()
        val single = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        runBlocking(pool) {
            inDefault.set(0)
            val barrier = CountDownLatch(1)
            val threadsOccupiedBarrier = CyclicBarrier(POOL_SIZE)
            // All threads but one
            repeat(POOL_SIZE - 1) {
                launch(pool /*Dispatchers.Default*/) {
                    threadsOccupiedBarrier.await()
                    barrier.runAndCheck()
                }
            }
            threadsOccupiedBarrier.await()
            withContext(pool /*Dispatchers.Default*/) {
                // Put a task in a local queue
                launch(pool /*Dispatchers.Default*/) {
                    barrier.runAndCheck()
                }
                // Put one more task to trick the local queue check
                launch(pool /*Dispatchers.Default*/) {
                    barrier.runAndCheck()
                }

                withContext(single /*Dispatchers.IO*/) {
                    yield()
                    barrier.countDown()
                }
            }
        }

        pool.close()
        single.close()
    }
}