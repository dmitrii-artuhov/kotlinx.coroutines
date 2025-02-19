package kotlinx.coroutines.gpmc.internal

import kotlinx.coroutines.internal.LockFreeLinkedListHead
import kotlinx.coroutines.internal.LockFreeLinkedListLongStressTest.*
import kotlinx.coroutines.internal.LockFreeLinkedListNode
import org.jetbrains.kotlinx.lincheck.ExperimentalModelCheckingAPI
import org.jetbrains.kotlinx.lincheck.runConcurrentTest
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.*
import kotlin.concurrent.thread


@OptIn(ExperimentalModelCheckingAPI::class)
class LockFreeLinkedListLongModelCheckingTest {
    data class IntNode(val i: Int) : LockFreeLinkedListNode()
    private val nAdded = 3
    private val nAddThreads = 1
    private val nRemoveThreads = 1
    private val removeProbability = 0.2

    private fun shallRemove(i: Int) = i and 63 != 42

    @Test
    fun testModelCheckingCustom() {
        val itemsCount = 5
        val sizes = mutableSetOf<Int>()
        runConcurrentTest {
            val list = LockFreeLinkedListHead()

            val t1 = thread {
                for (i in 0 until itemsCount) {
                    list.addLast(IntNode(i), Int.MAX_VALUE)
                }
            }

            val t2 = thread {
                list.forEach { node ->
                    node.remove()
                }
            }

            t1.join()
            t2.join()
            list.validate()
            var size = 0
            list.forEach { if (!it.isRemoved) size++ }
            sizes.add(size)
        }
        check(sizes.containsAll(List(itemsCount + 1) { it }))
    }

    @Test
    fun testModelChecking() {
        runConcurrentTest {
            // println("--- LockFreeLinkedListLongStressTest")
            val threads = mutableListOf<Thread>()
            val list = LockFreeLinkedListHead()
            val workingAdders = AtomicInteger(nAddThreads)

            for (j in 0 until nAddThreads)
                threads += thread(start = false, name = "adder-$j") {
                    for (i in j until nAdded step nAddThreads) {
                        list.addLast(IntNode(i), Int.MAX_VALUE)
                    }
                    // println("${Thread.currentThread().name} completed")
                    workingAdders.decrementAndGet()
                }
            for (j in 0 until nRemoveThreads)
                threads += thread(start = false, name = "remover-$j") {
                    //val rnd = Random()
                    do {
                        val lastTurn = workingAdders.get() == 0
                        list.forEach { node ->
                            if (node is IntNode && shallRemove(node.i) && (lastTurn /*|| rnd.nextDouble() < removeProbability*/))
                                node.remove()
                        }
                    } while (!lastTurn)
                    // println("${Thread.currentThread().name} completed")
                }
            // println("Starting ${threads.size} threads")
            for (thread in threads)
                thread.start()
            // println("Joining threads")
            for (thread in threads)
                thread.join()
            // verification
            // println("Verify result")
            list.validate()
            var size = 0
            list.forEach { if (!it.isRemoved) size++ }
            println("Size: $size")
            val expected = iterator {
                for (i in 0 until nAdded)
                    if (!shallRemove(i))
                        yield(i)
            }
            list.forEach { node ->
                require(node !is kotlinx.coroutines.internal.LockFreeLinkedListLongStressTest.IntNode || node.i == expected.next())
            }
            require(!expected.hasNext())
        }
    }

    private fun LockFreeLinkedListHead.validate() {
        var prev: LockFreeLinkedListNode = this
        var cur: LockFreeLinkedListNode = next as LockFreeLinkedListNode
        while (cur != this) {
            val next = cur.nextNode
            cur.validateNode(prev, next)
            prev = cur
            cur = next
        }
        validateNode(prev, next as LockFreeLinkedListNode)
    }
}
