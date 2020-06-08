package io.micronaut.docs.client.filter

//tag::class[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.reactivex.Flowable
import org.reactivestreams.Publisher

import javax.inject.Provider

@Requires(env = Environment.GOOGLE_COMPUTE)
@Filter(patterns = "/google-auth/api/**")
class GoogleAuthFilter implements HttpClientFilter {
    private final Provider<RxHttpClient> authClientProvider

    GoogleAuthFilter(Provider<RxHttpClient> httpClientProvider) { // <1>
        this.authClientProvider = httpClientProvider
    }

    @Override
    Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        Flowable<String> token = Flowable.fromCallable(() -> encodeURI(request))
                .flatMap(authURI -> authClientProvider.get().retrieve(HttpRequest.GET(authURI).header( // <2>
                        "Metadata-Flavor", "Google"
                )))

        return token.flatMap(t -> chain.proceed(request.bearerAuth(t)))
    }

    private static String encodeURI(MutableHttpRequest<?> request) {
        String receivingURI = "$request.uri.scheme://$request.uri.host"
        return "http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience=" +
                URLEncoder.encode(receivingURI, "UTF-8")
    }
}
//end::class[]