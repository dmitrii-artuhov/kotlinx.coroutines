package kotlinx.coroutines.gpmc.channels

import gpmc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.junit.Test
import java.util.concurrent.*
import kotlin.test.*

/**
 * Would hang if [Dispatchers.Default] was used.
 */
class SimpleSendReceiveJvmTestModelChecking : GPMCTestBase() {
    private val kind: TestChannelKind = TestChannelKind.RENDEZVOUS
    val n: Int = 2 // 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 100, 1000
    val concurrent: Boolean = true

    @Test
    fun testSimpleSendReceive() = runGPMCTest(100) {
        val channel = kind.create<Int>()
        val pool = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

        runBlocking(pool) {
            //val ctx = if (concurrent) Dispatchers.Default else coroutineContext
            launch(/*ctx*/ pool) {
                repeat(n) { channel.send(it) }
                channel.close()
            }
            var expected = 0
            for (x in channel) {
                if (!kind.isConflated) {
                    assertEquals(expected++, x)
                } else {
                    assertTrue(x >= expected)
                    expected = x + 1
                }
            }
            if (!kind.isConflated) {
                assertEquals(n, expected)
            }
        }

        pool.close()
    }
}
