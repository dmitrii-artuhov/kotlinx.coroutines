/*
 * Copyright 2016-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.gpmc

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DispatchException
import kotlinx.coroutines.testing.TestException
import org.jetbrains.kotlinx.lincheck.ExperimentalModelCheckingAPI
import org.jetbrains.kotlinx.lincheck.runConcurrentTest
import org.junit.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalModelCheckingAPI::class)
class ReproducerGpmc  {

    /**
     * Checks that even if the dispatcher sporadically fails, the limited dispatcher will still allow reaching the
     * target parallelism level.
     */
    @Test
    fun testLimitedParallelismOfOccasionallyFailingDispatcher() {
        val limit = 5
        var doFail = false
        val workerQueue = mutableListOf<Runnable>()
        val limited = object: CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                if (doFail) throw TestException()
                workerQueue.add(block)
            }
        }.limitedParallelism(limit)
        repeat(6 * limit) {
            try {
                limited.dispatch(EmptyCoroutineContext, Runnable { /* do nothing */ })
            } catch (_: DispatchException) {
                // ignore
            }
            doFail = !doFail
        }
        assertEquals(limit, workerQueue.size)
    }

    var iter = 0

    @Test
    fun testGpmc() {
        runConcurrentTest {
            println("Iteration: ${iter++}")
            val limit = 5
            var doFail = false
            val workerQueue = mutableListOf<Runnable>()
            val limited = object: CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: kotlinx.coroutines.Runnable) {
                    if (doFail) throw TestException()
                    workerQueue.add(block)
                }
            }.limitedParallelism(limit)
            repeat(limit) {
                try {
                    limited.dispatch(EmptyCoroutineContext, Runnable { /* do nothing */ })
                } catch (e: DispatchException) {
                    // ignore
                    println("Ignoring: ${e.message}: $e")
                }
                doFail = !doFail
            }
            assertEquals(limit, workerQueue.size)
        }
    }
}