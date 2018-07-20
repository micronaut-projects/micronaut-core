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
import spock.lang.Unroll

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
            ROOT: [configuredLevel: INFO, effectiveLevel: INFO],
            errors: [configuredLevel: ERROR, effectiveLevel: ERROR],
            'no-appenders': [configuredLevel: WARN, effectiveLevel: WARN],
            'no-level': [configuredLevel: NOT_SPECIFIED, effectiveLevel: INFO],
            'no-config': [configuredLevel: NOT_SPECIFIED, effectiveLevel: INFO],
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
        Map result = response.body()
        println ">> Result: $result"

        then:
        result.containsKey 'loggers'

        and: 'we have all the loggers expected from configuration'
        configuredLoggers.each { log, _ ->
            assert result.loggers.containsKey(log)
            assert result.loggers[log] == configuredLoggers[log]
        }
    }

    @Unroll
    void 'test that a configured logger #name can be retrieved by name from the endpoint'() {
        when:
        def response = client.exchange(GET("/loggers/${name}"), Map).blockingFirst()

        then:
        response.status == HttpStatus.OK

        when:
        def result = response.body()

        then:
        result.configuredLevel == configuredLevel
        result.effectiveLevel == effectiveLevel

        where:
        name     | configuredLevel | effectiveLevel
        'foo'    | NOT_SPECIFIED   | INFO
        'ROOT'   | INFO            | INFO
        'errors' | ERROR           | ERROR
    }

}
