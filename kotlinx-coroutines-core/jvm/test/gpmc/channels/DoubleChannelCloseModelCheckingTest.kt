package kotlinx.coroutines.channels

import gpmc.*
import kotlinx.coroutines.*
import org.junit.*
import java.util.concurrent.*

/**
 * Would hang if [GlobalScope] was used.
 */
class DoubleChannelCloseModelCheckingTest : GPMCTestBase() {

    @Test
    fun testDoubleCloseModelChecking() = runGPMCTest(100) {
        val pool = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

        runBlocking(pool) {
            val actor: SendChannel<Int> = /*GlobalScope*/ actor<Int>(pool + CoroutineName("actor"), start = CoroutineStart.LAZY) {
                // empty -- just closes channel
            }
            val sender = /*GlobalScope.*/ launch(pool + CoroutineName("sender")) {
                try {
                    actor.send(1)
                } catch (e: ClosedSendChannelException) {
                    // ok -- closed before send
                }
            }
            Thread.sleep(1)
            actor.close()
        }

        pool.close()
    }
}
