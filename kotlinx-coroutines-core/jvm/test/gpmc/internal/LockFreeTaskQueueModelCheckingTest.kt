package kotlinx.coroutines.gpmc.internal

import gpmc.*
import kotlinx.coroutines.internal.*
import org.jetbrains.kotlinx.lincheck.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.*
import kotlin.test.*


class LockFreeTaskQueueModelCheckingTest : GPMCTestBase() {
    private val nConsumers = 1
    private val singleConsumer = nConsumers == 1

    private val nProducers = 1
    private val batchSize = 2

    private class Item(val producer: Int, val index: Long)

    @Ignore("= Concurrent test has hung = (smaller invocation count gives no error)")
    @Test
    fun testModelChecking() = runGPMCTest(10_000) {
        val batch = AtomicInteger(0)
        val produced = AtomicLong(0L)
        val consumed = AtomicLong(0L)
        var expected = LongArray(nProducers)

        val queue = AtomicReference<LockFreeTaskQueue<Item>?>(null)
        val done = AtomicInteger(0)
        val doneProducers = AtomicInteger(0)

        val barrier = CyclicBarrier(nProducers + nConsumers + 1)

        val threads = mutableListOf<Thread>()
        threads += thread(name = "Pacer", start = false) {
            while (done.get() == 0) {
                queue.set(LockFreeTaskQueue(singleConsumer))
                batch.set(0)
                doneProducers.set(0)
                barrier.await() // start consumers & producers
                barrier.await() // await consumers & producers
            }
            queue.set(null)
            barrier.await() // wakeup the rest
        }
        threads += List(nConsumers) { consumer ->
            thread(name = "Consumer-$consumer", start = false) {
                while (true) {
                    barrier.await()
                    val queue = queue.get() ?: break
                    while (true) {
                        val item = queue.removeFirstOrNull()
                        if (item == null) {
                            if (doneProducers.get() == nProducers && queue.isEmpty) break // that's it
                            continue // spin to retry
                        }
                        consumed.incrementAndGet()
                        if (singleConsumer) {
                            // This check only properly works in single-consumer case
                            val eItem = expected[item.producer]++
                            if (eItem != item.index) error("Expected $eItem but got ${item.index} from Producer-${item.producer}")
                        }
                    }
                    barrier.await()
                }
            }
        }
        threads += List(nProducers) { producer ->
            thread(name = "Producer-$producer", start = false) {
                var index = 0L
                while (true) {
                    barrier.await()
                    val queue = queue.value ?: break
                    while (true) {
                        if (batch.incrementAndGet() >= batchSize) break
                        check(queue.addLast(Item(producer, index++))) // never closed
                        produced.incrementAndGet()
                    }
                    doneProducers.incrementAndGet()
                    barrier.await()
                }
            }
        }
        threads.forEach {
            it.setUncaughtExceptionHandler { t, e ->
                done.set(1)
                //error("Thread $t failed")
            }
        }
        threads.forEach { it.start() }

        done.set(1)
        threads.forEach { it.join() }
        assertEquals(produced.get(), consumed.get())
    }
}