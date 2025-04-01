package kotlinx.coroutines.channels

import gpmc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.*
import kotlinx.coroutines.testing.*
import org.junit.*
import java.util.concurrent.atomic.*

/**
 * Tests delivery of events to multiple broadcast channel subscribers.
 */
class BroadcastChannelMultiReceiveModelCheckingTest : GPMCTestBase() {
    private val nReceivers = 1
    // private val nSeconds = 3

    @Ignore("In Debug mode '= Concurrent test has hung =', in Regular mode just spins endlessly")
    @Test
    fun testModelChecking() {
        runGPMCTest(1) {
            val pool = newFixedThreadPoolContext(nReceivers + 1, "BroadcastChannelMultiReceiveModelCheckingTest")
            val kind: TestBroadcastChannelKind = TestBroadcastChannelKind.ARRAY_1
            val broadcast = kind.create<Long>()

            val sentTotal = AtomicLong()
            val receivedTotal = AtomicLong()
            val stopOnReceive = AtomicLong(-1)
            val lastReceived = Array(nReceivers) { AtomicLong(-1) }

            runBlocking(pool) {
                println("--- BroadcastChannelMultiReceiveStressTest $kind with nReceivers=$nReceivers")
                val sender =
                    launch(pool + CoroutineName("Sender")) {
                        var i = 0L
                        while (isActive) {
                            i++
                            broadcast.send(i) // could be cancelled
                            sentTotal.set(i) // only was for it if it was not cancelled
                        }
                    }
                val receivers = mutableListOf<Job>()
                // fun printProgress() {
                //     println("Sent ${sentTotal.get()}, received ${receivedTotal.get()}, receivers=${receivers.size}")
                // }
                // ramp up receivers
                repeat(nReceivers) {
                    delay(100)
                    val receiverIndex = receivers.size
                    val name = "Receiver$receiverIndex"
                    println("Launching $name")
                    receivers += launch(pool + CoroutineName(name)) {
                        val channel = broadcast.openSubscription()
                        when (receiverIndex % 5) {
                            0 -> doReceive(channel, receiverIndex, kind, lastReceived, receivedTotal, stopOnReceive)
                            1 -> doReceiveCatching(channel, receiverIndex, kind, lastReceived, receivedTotal, stopOnReceive)
                            2 -> doIterator(channel, receiverIndex, kind, lastReceived, receivedTotal, stopOnReceive)
                            3 -> doReceiveSelect(channel, receiverIndex, kind, lastReceived, receivedTotal, stopOnReceive)
                            4 -> doReceiveCatchingSelect(channel, receiverIndex, kind, lastReceived, receivedTotal, stopOnReceive)
                        }
                        channel.cancel()
                    }
                    // printProgress()
                }
                // repeat(nSeconds) {
                //     delay(1000)
                //     printProgress()
                // }
                sender.cancelAndJoin()
                println("Tested $kind with nReceivers=$nReceivers")
                val total = sentTotal.get()
                println("      Sent $total events, waiting for receivers")
                stopOnReceive.set(total)
                try {
                    // withTimeout(5000) {
                    receivers.forEachIndexed { index, receiver ->
                        if (lastReceived[index].get() >= total) receiver.cancel()
                        receiver.join()
                    }
                    // }
                } catch (e: Exception) {
                    println("Failed: $e")
                    pool.dumpThreads("Threads in pool")
                    receivers.indices.forEach { index ->
                        println("lastReceived[$index] = ${lastReceived[index].get()}")
                    }
                    throw e
                }
                 println("  Received ${receivedTotal.get()} events")
            }

            pool.close()
        }
    }

    private fun doReceived(
        receiverIndex: Int,
        i: Long,
        kind: TestBroadcastChannelKind,
        lastReceived: Array<AtomicLong>,
        receivedTotal: AtomicLong,
        stopOnReceive: AtomicLong
    ): Boolean {
        val last = lastReceived[receiverIndex].get()
        check(i > last) { "Last was $last, got $i" }
        if (last != -1L && !kind.isConflated)
            check(i == last + 1) { "Last was $last, got $i" }
        receivedTotal.incrementAndGet()
        lastReceived[receiverIndex].set(i)
        return i >= stopOnReceive.get()
    }

    private suspend fun doReceive(
        channel: ReceiveChannel<Long>,
        receiverIndex: Int,
        kind: TestBroadcastChannelKind,
        lastReceived: Array<AtomicLong>,
        receivedTotal: AtomicLong,
        stopOnReceive: AtomicLong
    ) {
        while (true) {
            try {
                val stop = doReceived(receiverIndex, channel.receive(), kind, lastReceived, receivedTotal, stopOnReceive)
                if (stop) break
            } catch (_: ClosedReceiveChannelException) {
                break
            }
        }
    }

    private suspend fun doReceiveCatching(
        channel: ReceiveChannel<Long>,
        receiverIndex: Int,
        kind: TestBroadcastChannelKind,
        lastReceived: Array<AtomicLong>,
        receivedTotal: AtomicLong,
        stopOnReceive: AtomicLong
    ) {
        while (true) {
            val stop = doReceived(
                receiverIndex, channel.receiveCatching().getOrNull() ?: break, kind, lastReceived, receivedTotal, stopOnReceive
            )
            if (stop) break
        }
    }

    private suspend fun doIterator(
        channel: ReceiveChannel<Long>,
        receiverIndex: Int,
        kind: TestBroadcastChannelKind,
        lastReceived: Array<AtomicLong>,
        receivedTotal: AtomicLong,
        stopOnReceive: AtomicLong
    ) {
        for (event in channel) {
            val stop = doReceived(receiverIndex, event, kind, lastReceived, receivedTotal, stopOnReceive)
            if (stop) break
        }
    }

    private suspend fun doReceiveSelect(
        channel: ReceiveChannel<Long>,
        receiverIndex: Int,
        kind: TestBroadcastChannelKind,
        lastReceived: Array<AtomicLong>,
        receivedTotal: AtomicLong,
        stopOnReceive: AtomicLong
    ) {
        while (true) {
            try {
                val event = select<Long> { channel.onReceive { it } }
                val stop = doReceived(receiverIndex, event, kind, lastReceived, receivedTotal, stopOnReceive)
                if (stop) break
            } catch (_: ClosedReceiveChannelException) {
                break
            }
        }
    }

    private suspend fun doReceiveCatchingSelect(
        channel: ReceiveChannel<Long>,
        receiverIndex: Int,
        kind: TestBroadcastChannelKind,
        lastReceived: Array<AtomicLong>,
        receivedTotal: AtomicLong,
        stopOnReceive: AtomicLong
    ) {
        while (true) {
            val event = select<Long?> { channel.onReceiveCatching { it.getOrNull() } } ?: break
            val stop = doReceived(receiverIndex, event, kind, lastReceived, receivedTotal, stopOnReceive)
            if (stop) break
        }
    }
}
