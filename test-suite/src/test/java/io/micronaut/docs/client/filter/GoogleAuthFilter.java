/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.client.filter;

//tag::class[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import javax.inject.Provider;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;

@Requires(env = Environment.GOOGLE_COMPUTE)
@Filter(patterns = "/google-auth/api/**")
public class GoogleAuthFilter implements HttpClientFilter {
    private final Provider<RxHttpClient> authClientProvider;

    public GoogleAuthFilter(Provider<RxHttpClient> httpClientProvider) { // <1>
        this.authClientProvider = httpClientProvider;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        Flowable<String> token = Flowable.fromCallable(() -> encodeURI(request))
                .flatMap(authURI -> authClientProvider.get().retrieve(HttpRequest.GET(authURI).header( // <2>
                        "Metadata-Flavor", "Google"
                )));

        return token.flatMap(t -> chain.proceed(request.bearerAuth(t)));
    }

    private String encodeURI(MutableHttpRequest<?> request) throws UnsupportedEncodingException {
        URI fullURI = request.getUri();
        String receivingURI = fullURI.getScheme() + "://" + fullURI.getHost();
        return "http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience=" +
                URLEncoder.encode(receivingURI, "UTF-8");
    }
}
//end::class[]
