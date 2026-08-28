package io.micronaut.docs.aop.around_reactive

import jakarta.inject.Singleton

@Singleton
open class TxExample(private val txManager: TxManager) {

    @Tx
    open suspend fun doWork(taskName: String): String {
        val txName = txManager.findTx()
        return "Doing job: $taskName in transaction: $txName"
    }

}
