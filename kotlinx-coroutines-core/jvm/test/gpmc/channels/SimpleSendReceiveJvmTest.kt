package kotlinx.coroutines.channels

import gpmc.*
import kotlinx.coroutines.testing.*
import kotlinx.coroutines.*
import org.jetbrains.kotlinx.lincheck.*
import org.junit.Test
import org.junit.runner.*
import org.junit.runners.*
import kotlin.test.*

class SimpleSendReceiveJvmTestModelChecking : GPMCTestBase() {
    private val kind: TestChannelKind = TestChannelKind.RENDEZVOUS
    val n: Int = 2
    val concurrent: Boolean = true

    @Ignore("Hangs and throws multiple `ThreadAbortedError`s")
    @Test
    fun testSimpleSendReceive() = runGPMCTest(1) {
        val channel = kind.create<Int>()

        runBlocking {
            val ctx = if (concurrent) Dispatchers.Default else coroutineContext
            launch(ctx) {
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
    }
}
