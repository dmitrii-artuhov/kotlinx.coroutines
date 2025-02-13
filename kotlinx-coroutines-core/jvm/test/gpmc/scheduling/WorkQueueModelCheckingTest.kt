package kotlinx.coroutines.scheduling

import org.jetbrains.kotlinx.lincheck.*
import org.junit.Test
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.jvm.internal.*
import kotlin.test.*

@OptIn(ExperimentalModelCheckingAPI::class)
class WorkQueueModelCheckingTest {
    fun println(msg: String) {
        System.out.println(msg)
        System.out.flush()
    }

    @Test
    @Ignore("Constructor of CountDownLatch causes infinite execution time even in single thread")
    fun testSingleProducerSingleStealer() {
        runConcurrentTest(1) {
            val threads = mutableListOf<Thread>()
            val offerIterations = 3 // memory pressure, not CPU time
            val producerQueue = WorkQueue()
            println("GPMC working")

            val startLatch = CountDownLatch(1)
//            threads += thread(name = "producer") {
//                println("Producer blocked")
//                //startLatch.await()
////                for (i in 1..offerIterations) {
////                    while (producerQueue.size == BUFFER_CAPACITY - 1) {
////                        Thread.yield()
////                    }
////
////                    // No offloading to global queue here
////                    producerQueue.add(task(i.toLong()))
////                }
//                println("Producer ended")
//            }

            //val stolen = GlobalQueue()
//            threads += thread(name = "stealer") {
////                val myQueue = WorkQueue()
////                val ref = Ref.ObjectRef<Task?>()
//                println("Stealer blocked")
//                //startLatch.await()
////                while (stolen.size != offerIterations) {
////                    if (producerQueue.trySteal(ref) != NOTHING_TO_STEAL) {
////                        stolen.addAll(myQueue.drain(ref).map { task(it) })
////                    }
////                }
////                stolen.addAll(myQueue.drain(ref).map { task(it) })
//                println("Producer ended")
//            }

            println("GPMC initialized threads")
            //startLatch.countDown()
            println("[GPMC]: allow threads to start!")
            threads.forEach { it.join() }
            //assertEquals((1L..offerIterations).toSet(), stolen.map { it.submissionTime }.toSet())
        }
    }

    private fun GlobalQueue.addAll(tasks: Collection<Task>) {
        tasks.forEach { addLast(it) }
    }
}
