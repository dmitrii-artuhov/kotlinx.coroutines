package kotlinx.coroutines.channels

import gpmc.*
import kotlinx.coroutines.*
import org.junit.*
import java.util.concurrent.atomic.*

class ConflatedChannelCloseModelCheckingTest : GPMCTestBase() {
    private val nSenders = 1

    @Ignore("All unfinished threads are in deadlock: huge execution history which does fit in console height")
    @Test
    fun testModelCheckingClose() = runGPMCTest(1) {
        val curChannel = AtomicReference<Channel<Int>>(Channel(Channel.CONFLATED))
        val sent = AtomicInteger()
        val closed = AtomicInteger()
        val received = AtomicInteger()
        val pool = newFixedThreadPoolContext(nSenders + 2, "TestStressClose")

        pool.use {
            runBlocking {
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

                senders.forEach { it.cancel() }
                closer.cancel()
                // wait them to complete
                senderJobs.forEach { it.join() }
                closerJob.join()
                // close cur channel
                flipChannel(curChannel)
                curChannel.get().close(StopException())
                // wait for receiver do complete
                try {
                    receiver.await()
                    error("Receiver should not complete normally")
                } catch (e: StopException) {
                    // ok
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

    class StopException : Exception()
}
