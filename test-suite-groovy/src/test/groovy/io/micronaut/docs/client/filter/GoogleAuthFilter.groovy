package io.micronaut.docs.client.filter

import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment

//tag::class[]

import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

@Requires(env = Environment.GOOGLE_COMPUTE)
@ClientFilter(patterns = "/google-auth/api/**")
class GoogleAuthFilter {

    private final BeanProvider<HttpClient> authClientProvider

    GoogleAuthFilter(BeanProvider<HttpClient> httpClientProvider) { // <1>
        this.authClientProvider = httpClientProvider
    }

    @RequestFilter
    @ExecuteOn(TaskExecutors.BLOCKING)
    void filter(MutableHttpRequest<?> request) {
        String authURI = encodeURI(request)
        String token = authClientProvider.get().toBlocking().retrieve(HttpRequest.GET(authURI).header( // <2>
                "Metadata-Flavor", "Google"
        ))

        request.bearerAuth(token)
    }

    private static String encodeURI(MutableHttpRequest<?> request) {
        String receivingURI = "$request.uri.scheme://$request.uri.host"
        "http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience=" +
                URLEncoder.encode(receivingURI, "UTF-8")
    }
}
//end::class[]
