package kotlinx.coroutines.gpmc.channels

import gpmc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.junit.*
import java.util.concurrent.atomic.*

class ConflatedChannelCloseModelCheckingTest : GPMCTestBase() {
    private val nSenders = 1

    @Test
    fun testModelCheckingClose() = runGPMCTest(1000) {
        val curChannel = AtomicReference<Channel<Int>>(Channel(Channel.CONFLATED))
        val sent = AtomicInteger()
        val closed = AtomicInteger()
        val received = AtomicInteger()
        val pool = newFixedThreadPoolContext(nSenders + 2, "TestStressClose")

        runBlocking(pool) {
            val sendersStatus = MutableList(nSenders) { AtomicBoolean(false) }
            val senderJobs = List(nSenders) { Job() }
            val senders = List(nSenders) { senderId ->
                launch(pool) {
                    try {
                        sendersStatus[senderId].set(true)
                        var x = senderId
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
            val closerStatus = AtomicBoolean(false)
            val closerJob = Job()
            val closer = launch(pool) {
                try {
                    closerStatus.set(true)
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

            // Note: here we must wait until all coroutines are started, before cancelling them, since no delay
            //       is possible, we need some flags! Because in coroutines we use try {} finally {} blocks!
            senders.forEachIndexed { index, it ->
                while (!sendersStatus[index].get()); // wait for sender to start before cancelling it
                check(sendersStatus[index].get())
                it.cancel()
            }

            while (!closerStatus.get()); // wait for closer to start before cancelling it
            check(closerStatus.get())
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

        pool.close()
    }

    private fun flipChannel(curChannel: AtomicReference<Channel<Int>>) {
        val oldChannel = curChannel.get()
        val newChannel = Channel<Int>(Channel.CONFLATED)
        curChannel.set(newChannel)
        check(oldChannel.close())
    }

    class StopException : Exception()
}
