package kotlinx.coroutines.channels

import gpmc.*
import kotlinx.coroutines.*
import org.junit.*

class BufferedChannelModelCheckingTest : GPMCTestBase() {
    private val capacity: Int = 2

    @Ignore(
        "Internal lincheck error: Check failed.\n" +
        "java.lang.IllegalStateException: Check failed.\n" +
        "at org.jetbrains.kotlinx.lincheck.strategy.managed.ManagedStrategy.runInvocation(ManagedStrategy.kt:245)"
    )
    @Test
    fun testModelCheck() = runGPMCTest {
        runBlocking {
            val n = 3
            val q = Channel<Int>(capacity)
            val sender = launch(Dispatchers.Default) {
                for (i in 1..n) {
                    q.send(i)
                }
            }
            val receiver = launch(Dispatchers.Default) {
                for (i in 1..n) {
                    val next = q.receive()
                    check(next == i)
                }
            }
            sender.join()
            receiver.join()
        }
    }

    @Ignore(
        "Hangs or lincheck fails internally with: Check failed.\n" +
        "java.lang.IllegalStateException: Check failed.\n" +
        "at org.jetbrains.kotlinx.lincheck.strategy.managed.ManagedStrategy.runInvocation(ManagedStrategy.kt:245)"
    )
    @Test
    fun joinJob() = runGPMCTest(1) {
        runBlocking {
            val job = launch(Dispatchers.Default) {}
            job.join()
        }
    }

    @Ignore(
        "Check failed.\n" +
        "java.lang.IllegalStateException: Check failed.\n" +
        "at org.jetbrains.kotlinx.lincheck.strategy.managed.ManagedStrategy.runInvocation(ManagedStrategy.kt:245)"
    )
    @Test
    fun singleThreadLaunchOnPool() = runGPMCTest(1) {
        runBlocking {
            val ch = Channel<Int>()
            val j1 = launch(Dispatchers.Default) {
                val v = ch.receive()
            }
            val j2 = launch(Dispatchers.Default) {
                ch.send(1)
            }
            joinAll(j1, j2)
        }
    }
}
