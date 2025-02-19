package gpmc

import org.jetbrains.kotlinx.lincheck.*


abstract class GPMCTestBase {
    @OptIn(ExperimentalModelCheckingAPI::class)
    fun runGPMCTest(
        invocations: Int = -1,
        shouldFail: Boolean = false,
        block: () -> Unit
    ) {
        val result = runCatching {
            if (invocations >= 0) runConcurrentTest(invocations, block)
            else runConcurrentTest(block = block)
        }
        if (result.isFailure != shouldFail) {
            throw result.exceptionOrNull()!!
        }
    }
}