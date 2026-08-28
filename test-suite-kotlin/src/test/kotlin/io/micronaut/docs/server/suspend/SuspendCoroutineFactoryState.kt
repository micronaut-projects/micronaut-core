package io.micronaut.docs.server.suspend

import io.micronaut.context.annotation.Factory
import io.micronaut.http.bind.binders.HttpCoroutineContextFactory
import io.micronaut.http.context.ServerRequestContext
import jakarta.inject.Singleton
import kotlin.coroutines.EmptyCoroutineContext

@Singleton
class SuspendCoroutineFactoryState {
    @Volatile
    var currentRequestPresent: Boolean? = null
}

@Factory
class SuspendCoroutineFactory {
    @Singleton
    fun suspendCoroutineContextFactory(state: SuspendCoroutineFactoryState): HttpCoroutineContextFactory<EmptyCoroutineContext> {
        return object : HttpCoroutineContextFactory<EmptyCoroutineContext> {
            override fun create(): EmptyCoroutineContext {
                state.currentRequestPresent = ServerRequestContext.currentRequest<Any>().isPresent
                return EmptyCoroutineContext
            }
        }
    }
}
