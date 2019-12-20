package io.micronaut.docs.client.filter

//tag::class[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import org.reactivestreams.Publisher

import javax.inject.Singleton

@BasicAuth // <1>
@Singleton // <2>
class BasicAuthClientFilter : HttpClientFilter {

    override fun doFilter(request: MutableHttpRequest<*>, chain: ClientFilterChain): Publisher<out HttpResponse<*>> {
        return chain.proceed(request.basicAuth("user", "pass"))
    }
}
//end::class[]
