package io.micronaut.http.client

import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.withContext
import org.reactivestreams.Publisher
import java.util.UUID

class SuspendClientFilter : HttpClientFilter {
    override fun doFilter(request: MutableHttpRequest<*>, chain: ClientFilterChain): Publisher<out HttpResponse<*>> {
        // if request contains filterCheck, then do flow step, else proceed
        if (request.body().contains(SuspendClientFilter.filterCheck)) {
            return flow { emit("testString") }.flatMapMerge {
                chain.proceed(request.header("myTestHeader", it)).asFlow()
            }.asPublisher()
        } else {
            return chain.proceed(request)
        }
    }

    companion object {
        val filterCheck = java.util.UUID.randomUUID().toString()
    }
}


