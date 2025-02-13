package kotlinx.coroutines.gpmc.internal

import kotlinx.atomicfu.*
import kotlinx.coroutines.internal.*
import org.jetbrains.kotlinx.lincheck.*
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.test.*


@Ignore("ThreadAbortedError caught in handler of `setUncaughtExceptionHandler`")
@OptIn(ExperimentalModelCheckingAPI::class)
class LockFreeTaskQueueModelCheckingTest {
    private val nConsumers = 1
    private val singleConsumer = nConsumers == 1

    private val nProducers = 1
    private val batchSize = 2

    private val batch = atomic(0)
    private val produced = atomic(0L)
    private val consumed = atomic(0L)
    private var expected = LongArray(nProducers)

    private val queue = atomic<LockFreeTaskQueue<Item>?>(null)
    private val done = atomic(0)
    private val doneProducers = atomic(0)

    private val barrier = CyclicBarrier(nProducers + nConsumers + 1)

    private class Item(val producer: Int, val index: Long)

    @Test
    fun testModelChecking() {
        runConcurrentTest(10) {
            val threads = mutableListOf<Thread>()
            threads += thread(name = "Pacer", start = false) {
                while (done.value == 0) {
                    queue.value = LockFreeTaskQueue(singleConsumer)
                    batch.value = 0
                    doneProducers.value = 0
                    barrier.await() // start consumers & producers
                    barrier.await() // await consumers & producers
                }
                queue.value = null
                println("Pacer done")
                barrier.await() // wakeup the rest
            }
            threads += List(nConsumers) { consumer ->
                thread(name = "Consumer-$consumer", start = false) {
                    while (true) {
                        barrier.await()
                        val queue = queue.value ?: break
                        while (true) {
                            val item = queue.removeFirstOrNull()
                            if (item == null) {
                                if (doneProducers.value == nProducers && queue.isEmpty) break // that's it
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
                    println("Consumer-$consumer done")
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
                    println("Producer-$producer done")
                }
            }
            threads.forEach {
                it.setUncaughtExceptionHandler { t, e ->
                    System.err.println("[GPMC] Thread $t failed: $e")
                    e.printStackTrace()
                    done.value = 1
                    e.printStackTrace()
                    error("Thread $t failed")
                }
            }
            threads.forEach { it.start() }
//            for (second in 1..nSeconds) {
//                Thread.sleep(1000)
//                println("$second: produced=${produced.value}, consumed=${consumed.value}")
//                if (done.value == 1) break
//            }
            done.value = 1
            threads.forEach { it.join() }
            println("T: produced=${produced.value}, consumed=${consumed.value}")
            assertEquals(produced.value, consumed.value)
        }
    }
}