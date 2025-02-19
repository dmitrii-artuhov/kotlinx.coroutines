package kotlinx.coroutines.channels

import kotlinx.coroutines.testing.*
import kotlinx.coroutines.*
import org.jetbrains.kotlinx.lincheck.*
import org.junit.*
import org.junit.runner.*
import org.junit.runners.*

@OptIn(ExperimentalModelCheckingAPI::class)
class BufferedChannelModelCheckingTest {
    private val capacity: Int = 2

    @Test
    fun testModelChecking() {
        var cnt = 0
        runConcurrentTest {
            runBlocking {
                println("GPMC: ${cnt++}")
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
    }

    @Test
    fun testBurstModelChecking() {
        runConcurrentTest {
            runBlocking {
                val channel = Channel<Int>(capacity)
                val sender = launch(Dispatchers.Default) {
                    for (i in 1..1) {
                        channel.send(i)
                    }
                }
                val receiver = launch(Dispatchers.Default) {
                    for (i in 1..1) {
                        val next = channel.receive()
                        check(next == i)
                    }
                }
                sender.join()
                receiver.join()
            }
        }
    }

    @Test
    fun joinJob() {
        runConcurrentTest {
            println("GPMC: ${Thread.currentThread().id}")
            runBlocking {
                println("runBlocking: ${Thread.currentThread().id}")
                println("runBlocking: run blocking")
                val job = launch(/*Dispatchers.Default*/ /* no difference in running on thread-pool or on the same thread */) {
                    println("Job: ${Thread.currentThread().id}")
                    println("Job: Launched!")
                }
                println("GPMC: endless join...")
                job.join()
            }
        }
    }

    @Test
    fun singleThreadYetCooperative() {
        runConcurrentTest {
            runBlocking {
                val ch = Channel<Int>()
                val j1 = launch {
                    println("Coroutine 1 is running on thread: ${Thread.currentThread().id}")
                    val v = ch.receive()
                    println("Value: $v")
                }
                val j2 = launch {
                    println("Coroutine 2 is running on thread: ${Thread.currentThread().id}")
                    ch.send(1)
                }
                println("Joining...")
                joinAll(j1, j2)
                println("Joined")
            }
        }
    }
}
