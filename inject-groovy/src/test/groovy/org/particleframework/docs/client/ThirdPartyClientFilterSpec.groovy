package org.particleframework.docs.httpclient

import io.reactivex.Flowable
import io.reactivex.functions.Consumer

/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.particleframework.context.ApplicationContext
import org.particleframework.context.annotation.Value
import org.particleframework.core.io.socket.SocketUtils
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.MutableHttpRequest
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Filter
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Header
import org.particleframework.http.client.Client
import org.particleframework.http.client.RxHttpClient
import org.particleframework.http.filter.ClientFilterChain
import org.particleframework.http.filter.HttpClientFilter
import org.particleframework.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Sergio del Amo
 * @since 1.0
 */
class ThirdPartyClientFilterSpec extends Specification {
    private static String token = 'XXXX'
    private static String username = 'john'
    @Shared int port = SocketUtils.findAvailableTcpPort()
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            'particle.server.port':port,
            'particle.http.clients.myService.url': "http://localhost:$port",
            'bintray.username': username,
            'bintray.token': token,
            'bintray.organization': 'grails',
    )
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    def "a client filter is applied to the request and adds the authorization header"() {
        given:
        BintrayService bintrayService = context.getBean(BintrayService)

        PollingConditions conditions = new PollingConditions(timeout: 1)

        when:
        String result
        bintrayService.fetchRepositories().subscribe(new Consumer<HttpResponse<String>>() {
            @Override
            void accept(HttpResponse<String> str) throws Exception {
                result = str.body()
            }
        })

        String encoded = "$username:$token".bytes.encodeBase64()
        String expected = "Basic $encoded".toString()
        then:
        conditions.eventually {
            assert result == expected
        }
    }

    @Controller('/repos')
    static class HeaderController {

        @Get(uri = "/grails")
        String echoAuthorization(@Header String authorization) {
            authorization
        }
    }
}

//tag::bintrayService[]
@Singleton
class BintrayService {

    @Inject
    @Client(BintrayApi.URL)
    RxHttpClient client

    @Value('bintray.organization')
    String org

    Flowable<HttpResponse<String>> fetchRepositories() {
        client.exchange(HttpRequest.GET("/repos/$org"), String)
    }

    Flowable<HttpResponse<String>> fetchPackages(String repo) {
        client.exchange(HttpRequest.GET("/repos/${org}/${repo}/packages"), String)
    }
}
//end::bintrayService[]

//tag::bintrayFilter[]
@Filter('/repos/**') // <1>
class BintrayFilter implements HttpClientFilter {

    @Value('bintray.username')
    String username

    @Value('bintray.token')
    String token

    @Override
    Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        String encoded = "$username:$token".bytes.encodeBase64()
        String authorization = "Basic $encoded".toString()
        request.header('Authorization', authorization)
        chain.proceed(request)
    }
}
//end::bintrayFilter[]

/*
//tag::bintrayApiConstants[]
class BintrayApi {
    public static final String URL = 'https://api.bintray.com'
}
//end::bintrayApiConstants[]
*/

class BintrayApi {
    public static final String URL = '/'
}