package kotlinx.coroutines.channels

import gpmc.*
import kotlinx.coroutines.*
import org.junit.*

class DoubleChannelCloseModelCheckingTest : GPMCTestBase() {

    @Ignore("Hangs")
    @Test
    fun testDoubleCloseStress() = runGPMCTest(1) {
        val actor: SendChannel<Int> = GlobalScope.actor<Int>(CoroutineName("actor"), start = CoroutineStart.LAZY) {
            // empty -- just closes channel
        }
        val sender = GlobalScope.launch(CoroutineName("sender")) {
            try {
                actor.send(1)
            } catch (e: ClosedSendChannelException) {
                // ok -- closed before send
            }
        }
        //Thread.sleep(1)
        actor.close()
    }
}
