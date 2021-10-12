package io.micronaut.docs.client.filter

//tag::class[]
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import java.net.URLEncoder

@Requires(env = [Environment.GOOGLE_COMPUTE])
@Filter(patterns = ["/google-auth/api/**"])
class GoogleAuthFilter (
    private val authClientProvider: BeanProvider<HttpClient>) : HttpClientFilter { // <1>

    override fun doFilter(request: MutableHttpRequest<*>,
                          chain: ClientFilterChain): Publisher<out HttpResponse<*>?> {
        return Mono.fromCallable { encodeURI(request) }
            .flux()
            .map { authURI: String ->
                authClientProvider.get().retrieve(HttpRequest.GET<Any>(authURI)
                    .header("Metadata-Flavor", "Google") // <2>
                )
            }.flatMap { t -> chain.proceed(request.bearerAuth(t.toString())) }
    }

    private fun encodeURI(request: MutableHttpRequest<*>): String {
        val receivingURI = "${request.uri.scheme}://${request.uri.host}"
        return "http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience=" +
                URLEncoder.encode(receivingURI, "UTF-8")
    }

}
//end::class[]
