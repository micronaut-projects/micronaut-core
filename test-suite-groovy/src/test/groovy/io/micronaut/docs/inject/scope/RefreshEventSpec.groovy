/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.docs.inject.scope

import groovy.json.JsonOutput
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

import javax.annotation.PostConstruct
import javax.inject.Inject
import java.text.SimpleDateFormat

import static io.micronaut.http.HttpResponse.ok

@Retry
class RefreshEventSpec extends Specification {
    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer, [
                    'spec.name': 'RefreshEventSpec'
            ], Environment.TEST)

    @Shared @AutoCleanup HttpClient client = HttpClient.create(embeddedServer.URL)

    def "publishing a refresh event, destroys beans with @Refreshable Scope"() {
        when: 'requesting a forecast for the first time'
            String firstResponse = fetchForecast()

        then: 'the server sends a valid response'
            firstResponse
            firstResponse.contains('"forecast": "Scattered Clouds')

        when: 'we ask for a forecast'
            String secondResponse = fetchForecast()

        then: 'we receive an identical response since the WeatherService is a Singleton storing the previous forecast'
            firstResponse == secondResponse

        when:
            String response = evictForecast()

        then:
            response == """
// tag::evictResponse[]
{
    "msg": "OK"
}
// end::evictResponse[]
""".replace('\n// tag::evictResponse[]\n', '')
                    .replace('\n// end::evictResponse[]\n', '')

        when: 'we ask for a forecast, since the evict endpoint triggered a Refresh event'
            String thirdResponse = fetchForecast()

        then: 'the server responds a different forecast because WeatherService was destroyed and instantiated again'
            thirdResponse != secondResponse
            thirdResponse.contains('"forecast": "Scattered Clouds')
    }

    String fetchForecast() {
        String response = client.toBlocking().retrieve(HttpRequest.GET("/weather/forecast"))
        JsonOutput.prettyPrint(response)
    }

    String evictForecast() {
        String response = client.toBlocking().retrieve(HttpRequest.POST("/weather/evict", [:]))
        JsonOutput.prettyPrint(response)

    }

    //tag::weatherService[]
    @Refreshable // <1>
    static class WeatherService {

        String forecast

        @PostConstruct
        void init() {
            forecast = "Scattered Clouds ${new SimpleDateFormat("dd/MMM/yy HH:mm:ss.SSS").format(new Date())}" // <2>
        }

        String latestForecast() {
            return forecast
        }
    }
    //end::weatherService[]


    @Requires(property = "spec.name", value = "RefreshEventSpec")
    @Controller('/weather')
    static class WeatherController {
        @Inject
        WeatherService weatherService

        @Inject
        ApplicationContext applicationContext

        @Get(value = "/forecast")
        HttpResponse<Map<String, String>> index() {
            ok([forecast: weatherService.latestForecast()]) as HttpResponse<Map<String, String>>
        }

        @Post("/evict")
        HttpResponse<Map<String, String>> evict() {
            //tag::publishEvent[]
            applicationContext.publishEvent(new RefreshEvent())
            //end::publishEvent[]
            ok([msg: 'OK']) as HttpResponse<Map<String, String>>
        }
    }
}
