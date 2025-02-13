package kotlinx.coroutines.gpmc.selects

import kotlinx.coroutines.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*
import org.jetbrains.kotlinx.lincheck.*
import org.junit.*
import org.junit.Test
import kotlin.test.*
import kotlin.test.Ignore


@Ignore("Selects are tricky to rewrite, I'll take a second look at it")
class SelectDeadlockModelCheckingTest {
    @OptIn(ExperimentalModelCheckingAPI::class)
    @Test
    fun testModelChecking() {
        val pool = newFixedThreadPoolContext(2, "SelectDeadlockStressTest")
        pool.use {
            runConcurrentTest(1) {
                runBlocking {
                    val c1 = Channel<Long>()
                    val c2 = Channel<Long>()
                    val s1 = Stats()
                    val s2 = Stats()
                    launchSendReceive(c1, c2, s1, it)
                    launchSendReceive(c2, c1, s2, it)
                    println("gpmc: First: $s1; Second: $s2")
                    coroutineContext.cancelChildren()
                }
            }
        }
    }

    private class Stats {
        var sendIndex = 0L
        var receiveIndex = 0L

        override fun toString(): String = "send=$sendIndex, received=$receiveIndex"
    }

    private fun CoroutineScope.launchSendReceive(c1: Channel<Long>, c2: Channel<Long>, s: Stats, pool: ExecutorCoroutineDispatcher) = launch(pool) {
        //while (true) {
            // if (s.sendIndex % 1000 == 0L) yield()
            //if (s.sendIndex == 0L) yield()
            select<Unit> {
                c1.onSend(s.sendIndex) {
                    s.sendIndex++
                }
                c2.onReceive { i ->
                    assertEquals(s.receiveIndex, i)
                    s.receiveIndex++
                }
            }
        //}
    }
}