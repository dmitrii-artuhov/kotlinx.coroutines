package kotlinx.coroutines.channels

import kotlinx.coroutines.testing.*
import kotlinx.coroutines.*
import org.jetbrains.kotlinx.lincheck.*
import org.junit.*
import java.util.concurrent.atomic.*

@OptIn(ExperimentalModelCheckingAPI::class)
class ConflatedChannelCloseModelCheckingTest {

    private val nSenders = 1
    // private val testSeconds = 3 * stressTestMultiplier


    @Ignore("Hangs on joining jobs")
    @Test
    fun testModelCheckingClose() {
        runConcurrentTest(1) {
            val curChannel = AtomicReference<Channel<Int>>(Channel(Channel.CONFLATED))
            val sent = AtomicInteger()
            val closed = AtomicInteger()
            val received = AtomicInteger()
            val pool = newFixedThreadPoolContext(nSenders + 2, "TestStressClose")

            pool.use {
                runBlocking {
                    println("--- ConflatedChannelCloseStressTest with nSenders=$nSenders")
                    val senderJobs = List(nSenders) { Job() }
                    val senders = List(nSenders) { senderId ->
                        launch(pool) {
                            var x = senderId
                            try {
                                while (isActive) {
                                    curChannel.get().trySend(x).onSuccess {
                                        x += nSenders
                                        sent.incrementAndGet()
                                    }
                                }
                            } finally {
                                senderJobs[senderId].cancel()
                            }
                        }
                    }
                    val closerJob = Job()
                    val closer = launch(pool) {
                        try {
                            while (isActive) {
                                flipChannel(curChannel)
                                closed.incrementAndGet()
                                yield()
                            }
                        } finally {
                            closerJob.cancel()
                        }
                    }
                    val receiver = async(pool + NonCancellable) {
                        while (isActive) {
                            curChannel.get().receiveCatching().getOrElse {
                                it?.let { throw it }
                            }
                            received.incrementAndGet()
                        }
                    }
                    // print stats while running
//                    repeat(testSeconds) {
//                        delay(1000)
//                        printStats()
//                    }
                    println("Stopping")
                    senders.forEach { it.cancel() }
                    closer.cancel()
                    // wait them to complete
                    println("waiting for senders...")
                    senderJobs.forEach { it.join() }
                    println("waiting for closer...")
                    closerJob.join()
                    // close cur channel
                    println("Closing channel and signalling receiver...")
                    flipChannel(curChannel)
                    curChannel.get().close(StopException())
                    /// wait for receiver do complete
                    println("Waiting for receiver...")
                    try {
                        receiver.await()
                        error("Receiver should not complete normally")
                    } catch (e: StopException) {
                        // ok
                    }
                    // print stats
                    println("--- done")
//                    printStats()
                }
            }
        }
    }

    private fun flipChannel(curChannel: AtomicReference<Channel<Int>>) {
        val oldChannel = curChannel.get()
        val newChannel = Channel<Int>(Channel.CONFLATED)
        curChannel.set(newChannel)
        check(oldChannel.close())
    }

//    private fun printStats() {
//        println("sent ${sent.get()}, closed ${closed.get()}, received ${received.get()}")
//    }

    class StopException : Exception()
}
