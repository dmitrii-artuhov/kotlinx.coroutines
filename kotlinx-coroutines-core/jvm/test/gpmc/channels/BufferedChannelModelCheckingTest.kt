package kotlinx.coroutines.gpmc.channels

import gpmc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.junit.*
import java.util.concurrent.*

/**
 * Would hang because of [Dispatchers.Default].
 * Passes with custom pool.
 */
class BufferedChannelModelCheckingTest : GPMCTestBase() {
    private val capacity: Int = 10 // 1, 10, 100, 100_000, 1_000_000

    @Test
    fun testModelCheck() = runGPMCTest(100) {
        val pool = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val n = 3
        val q = Channel<Int>(capacity)

        runBlocking(pool) {
            val sender = launch(pool /*Dispatchers.Default*/) {
                for (i in 1..n) {
                    q.send(i)
                }
            }
            val receiver = launch(pool /*Dispatchers.Default*/) {
                for (i in 1..n) {
                    val next = q.receive()
                    check(next == i)
                }
            }
            sender.join()
            receiver.join()
        }

        pool.close()
    }

    @Test
    fun testBurst() = runGPMCTest(100) {
        val nTimes = 3
        val pool = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

        runBlocking(pool) {
            repeat(nTimes) {
                val channel = Channel<Int>(capacity)
                val sender = launch(pool /*Dispatchers.Default*/) {
                    for (i in 1..capacity * 2) {
                        channel.send(i)
                    }
                }
                val receiver = launch(pool /*Dispatchers.Default*/) {
                    for (i in 1..capacity * 2) {
                        val next = channel.receive()
                        check(next == i)
                    }
                }
                sender.join()
                receiver.join()
            }
        }

        pool.close()
    }
}
