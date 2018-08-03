/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.docs.scopes

import groovy.json.JsonOutput
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import javax.annotation.PostConstruct
import javax.inject.Inject

import static io.micronaut.http.HttpResponse.ok

/**
 * @author Sergio del Amo
 * @since 1.0
 */
@Ignore
class RefreshEventSpec extends Specification {
    @Shared int port = SocketUtils.findAvailableTcpPort()
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            'micronaut.server.port':port,
            'micronaut.http.clients.myService.url': "http://localhost:$port"
    )
    @Shared @AutoCleanup EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    String getUrl() {
        "http://localhost:$port"
    }

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
        String curlCommand = '''
            // tag::forecastCurlCommand[]
curl "{url}/weather/forecast" 
            // end::forecastCurlCommand[]
        '''.toString().replace('{url}', url)

        Process process = [ 'bash', '-c', curlCommand ].execute()
        process.waitFor()

        JsonOutput.prettyPrint(process.text)
    }

    String evictForecast() {
        String curlCommand = '''
            // tag::evictCurlCommand[]
curl -X "POST" "{url}/weather/evict" 
            // end::evictCurlCommand[]
        '''.toString().replace('{url}', url)
        Process process = [ 'bash', '-c', curlCommand ].execute()
        process.waitFor()

        JsonOutput.prettyPrint(process.text)

    }

    //tag::weatherService[]
    @Refreshable // <1>
    static class WeatherService {

        String forecast

        @PostConstruct
        void init() {
            forecast = "Scattered Clouds ${new Date().format('dd/MMM/yy HH:ss.SSS')}" // <2>
        }

        String latestForecast() {
            return forecast
        }
    }
    //end::weatherService[]


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
