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

package io.micronaut.management.endpoint.loggers

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.http.HttpRequest.GET

class LoggersEndpointSpec extends Specification {

    @Shared EmbeddedServer server
    @Shared RxHttpClient client

    // Constants matching LogLevel
    static final ALL = 'ALL'
    static final ERROR = 'ERROR'
    static final WARN = 'WARN'
    static final INFO = 'INFO'
    static final DEBUG = 'DEBUG'
    static final TRACE = 'TRACE'
    static final OFF = 'OFF'
    static final NOT_SPECIFIED = 'NOT_SPECIFIED'

    // Loggers configured in logback-test.xml
    static final configuredLoggers = [
//            ROOT: [configuredLevel: INFO, effectiveLevel: INFO],
//            errors: [configuredLevel: INFO, effectiveLevel: INFO],
//            'no-appenders': [configuredLevel: INFO, effectiveLevel: INFO],
//            'no-level': [configuredLevel: INFO, effectiveLevel: INFO],
//            'no-config': [configuredLevel: INFO, effectiveLevel: INFO],

            // Currently stubbed in implementation
            foo: [configuredLevel: NOT_SPECIFIED, effectiveLevel: NOT_SPECIFIED]
    ]

    void setup() {
        server = ApplicationContext.run(EmbeddedServer)
        client = server.applicationContext.createBean(RxHttpClient, server.URL)
    }

    void cleanup() {
        client.close()
        server.close()
    }

    void 'test that available log levels are returned from the endpoint'() {
        when:
        def response = client.exchange(GET('/loggers'), Map).blockingFirst()

        then:
        response.status == HttpStatus.OK

        when:
        def result = response.body()

        then:
        result.containsKey 'levels'
        result.levels == [ALL, TRACE, DEBUG, INFO, WARN, ERROR, OFF, NOT_SPECIFIED]
    }

    void 'test that configured loggers are returned from the endpoint'() {
        when:
        def response = client.exchange(GET('/loggers'), Map).blockingFirst()

        then:
        response.status == HttpStatus.OK

        when:
        def result = response.body()

        then:
        result.containsKey 'loggers'

        and: 'we have all the loggers expected from configuration'
        configuredLoggers.every { log, levels ->
            result.loggers.containsKey log
            result.loggers."${log}" == levels
        }
    }

    void 'test that a configured logger can be retrieved by name from the endpoint'() {
        when:
        def name = 'foo'
        def response = client.exchange(GET("/loggers/${name}"), Map).blockingFirst()

        then:
        response.status == HttpStatus.OK

        when:
        def result = response.body()

        then:
        result.configuredLevel == configuredLoggers.foo.configuredLevel
        result.effectiveLevel == configuredLoggers.foo.effectiveLevel
    }

}
