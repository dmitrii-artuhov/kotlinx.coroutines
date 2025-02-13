package kotlinx.coroutines.gpmc.internal

import kotlinx.coroutines.internal.LockFreeLinkedListHead
import kotlinx.coroutines.internal.LockFreeLinkedListNode
import org.jetbrains.kotlinx.lincheck.ExperimentalModelCheckingAPI
import org.jetbrains.kotlinx.lincheck.runConcurrentTest
import org.junit.Test
import kotlin.concurrent.thread


@OptIn(ExperimentalModelCheckingAPI::class)
class LockFreeLinkedListLongModelCheckingTest {
    data class IntNode(val i: Int) : LockFreeLinkedListNode()

    @Test
    fun testModelChecking() {
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
