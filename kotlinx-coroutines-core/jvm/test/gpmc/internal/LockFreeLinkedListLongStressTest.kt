package gpmc.internal

import kotlinx.coroutines.internal.LockFreeLinkedListHead
import kotlinx.coroutines.internal.LockFreeLinkedListNode
import kotlinx.coroutines.testing.TestBase
import org.jetbrains.kotlinx.lincheck.ExperimentalModelCheckingAPI
import org.jetbrains.kotlinx.lincheck.runConcurrentTest
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * This stress test has 2 threads adding on one side on list, 2 more threads adding on the other,
 * and 6 threads iterating and concurrently removing items. The resulting list that is being
 * stressed is long.
 */
@OptIn(ExperimentalModelCheckingAPI::class)
class GPMCLockFreeLinkedListLongGPMCTest {
    data class IntNode(val i: Int) : LockFreeLinkedListNode()
//    val list = LockFreeLinkedListHead()
//
//    val threads = mutableListOf<Thread>()
//    private val nAdded = 10_000_000 // should not stress more, because that'll run out of memory
//    private val nAddThreads = 4 // must be power of 2 (!!!)
//    private val nRemoveThreads = 6
//    private val removeProbability = 0.2
//    private val workingAdders = AtomicInteger(nAddThreads)

//    private fun shallRemove(i: Int) = i and 63 != 42

//    @Test
//    fun newTest() {
//        runConcurrentTest {
//            var shared = 0
//            val t1 = thread { shared++ }
//            val t2 = thread { shared++ }
//            t1.join(); t2.join()
//            check(shared == 2)
//        }
//    }

    @Test
    fun testModelChecking() {
        val sizes = mutableMapOf<Int, Int>()
        runConcurrentTest {
            val list = LockFreeLinkedListHead()

            val t1 = thread {
                list.addLast(IntNode(0), Int.MAX_VALUE)
                list.addLast(IntNode(1), Int.MAX_VALUE)
                list.addLast(IntNode(2), Int.MAX_VALUE)
                list.addLast(IntNode(4), Int.MAX_VALUE)
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
            if (sizes.putIfAbsent(size, 1) == null) {
                println("New list size: $size")
            }
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
