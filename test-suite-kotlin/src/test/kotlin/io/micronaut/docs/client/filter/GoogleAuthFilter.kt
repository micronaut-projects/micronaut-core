package io.micronaut.docs.client.filter

//tag::class[]
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import java.net.URLEncoder

@Requires(env = [Environment.GOOGLE_COMPUTE])
@ClientFilter(patterns = ["/google-auth/api/**"])
class GoogleAuthFilter (
    private val authClientProvider: BeanProvider<HttpClient>) { // <1>

    @RequestFilter
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun filter(request: MutableHttpRequest<*>) {
        val authURI = encodeURI(request)
        val t = authClientProvider.get().toBlocking().retrieve(HttpRequest.GET<Any>(authURI)
            .header("Metadata-Flavor", "Google") // <2>
        )
        request.bearerAuth(t.toString())
    }

    private fun encodeURI(request: MutableHttpRequest<*>): String {
        val receivingURI = "${request.uri.scheme}://${request.uri.host}"
        return "http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience=" +
                URLEncoder.encode(receivingURI, "UTF-8")
    }

}
//end::class[]
