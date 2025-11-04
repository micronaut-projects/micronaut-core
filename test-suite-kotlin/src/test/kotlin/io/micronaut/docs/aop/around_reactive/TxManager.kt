package io.micronaut.docs.aop.around_reactive

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionStage
import java.util.function.Function
import kotlin.coroutines.coroutineContext

@Singleton
class TxManager {
    private var txCount = 0
    private val transactionsLog: MutableList<String> = ArrayList()

    suspend fun findTx(): String {
        val propagatedContext = KotlinCoroutinePropagation.findPropagatedContext(coroutineContext)!!
        check(
            propagatedContext.allElements.count() <= 1
        )
        return propagatedContext.find(TxPropagatedContext::class.java).get().tx
    }

    fun getTransactionsLog(): List<String> {
        return transactionsLog
    }

    fun <T> inTransaction(fn: Function<String, CompletionStage<T>>): CompletionStage<T> {
        val tx = newTransaction()
        LOGGER.info("Opening transaction: {}", tx)
        transactionsLog.add("OPEN $tx")
        PropagatedContext.getOrEmpty().plus(TxPropagatedContext(tx)).propagate().use {
            transactionsLog.add("IN $tx")
            return fn.apply(tx).whenComplete { value, throwable ->
                if (throwable != null) {
                    LOGGER.info("Rollback transaction: {}", tx)
                    transactionsLog.add("ROLLBACK $tx")
                } else {
                    LOGGER.info("Commit transaction: {}", tx)
                    transactionsLog.add("COMMIT $tx")
                }
            }
        }
    }

    private fun newTransaction(): String {
        return "TX" + ++txCount
    }

    @JvmRecord
    private data class TxPropagatedContext(val tx: String) : PropagatedContextElement
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TxManager::class.java)
    }
}
