package io.micronaut.docs.server.suspend.multiple

import jakarta.inject.Singleton
import java.util.*
import kotlin.collections.ArrayList

@Singleton
open class MyService(
        private val repository: CustomRepository
) {

    companion object {
        val events: MutableList<String> = Collections.synchronizedList(ArrayList())
    }

    open suspend fun someCall() {
        // Simulate accessing two different data-source repositories using two transactions
        tx1()
        // Call another coroutine
        repository.count1()
        repository.count2()
    }

    @Transaction1
    open suspend fun tx1() {
        tx2()
    }

    @Transaction2
    open suspend fun tx2() {
        repository.abc()
        repository.xyz()
    }

}
