package kotlinx.coroutines.channels

import kotlinx.coroutines.testing.*
import kotlinx.coroutines.*
import org.jetbrains.kotlinx.lincheck.*
import org.junit.*

@OptIn(ExperimentalModelCheckingAPI::class)
class DoubleChannelCloseModelCheckingTest {

    @Ignore("Hangs, even if `actor.close()` is called before the start of the actor")
    @Test
    fun testDoubleCloseStress() {
        runConcurrentTest(1) {
            val actor: SendChannel<Int> = GlobalScope.actor<Int>(CoroutineName("actor"), /*start = CoroutineStart.LAZY*/) {
                println("Empty actor")
                // empty -- just closes channel
            }
            val sender = GlobalScope.launch(CoroutineName("sender")) {
                try {
                    println("Sending to actor")
                    actor.send(1)
                    println("Sent to actor")
                } catch (e: ClosedSendChannelException) {
                    // ok -- closed before send
                    println("ClosedSendChannelException: ${e.message}")
                }
            }
            //Thread.sleep(1)
            (actor as Job).invokeOnCompletion {
                println("Actor completion handler")
                sender.invokeOnCompletion {
                    println("Sender completion handler")
                    println("Closing actor")
                    actor.close()
                    println("Finished")
                }
            }
        }
    }
}
