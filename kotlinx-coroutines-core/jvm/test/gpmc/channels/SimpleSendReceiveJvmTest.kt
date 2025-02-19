package kotlinx.coroutines.channels

import kotlinx.coroutines.testing.*
import kotlinx.coroutines.*
import org.jetbrains.kotlinx.lincheck.*
import org.junit.Test
import org.junit.runner.*
import org.junit.runners.*
import kotlin.test.*

@OptIn(ExperimentalModelCheckingAPI::class)
class SimpleSendReceiveJvmTestModelChecking(
) {
//    companion object {
//        @Parameterized.Parameters(name = "{0}, n={1}, concurrent={2}")
//        @JvmStatic
//        fun params(): Collection<Array<Any>> = TestChannelKind.values().flatMap { kind ->
//            listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 100, 1000).flatMap { n ->
//                listOf(false, true).map { concurrent ->
//                    arrayOf<Any>(kind, n, concurrent)
//                }
//            }
//        }
//    }

    private val kind: TestChannelKind = TestChannelKind.RENDEZVOUS
    val n: Int = 2
    val concurrent: Boolean = true

    @Ignore("Hangs and throws multiple `ThreadAbortedError`s")
    @Test
    fun testSimpleSendReceive() {
        runConcurrentTest(1) {
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
}
