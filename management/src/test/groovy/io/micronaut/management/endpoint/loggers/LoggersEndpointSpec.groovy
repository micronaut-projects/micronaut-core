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
package io.micronaut.management.endpoint.loggers

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.http.HttpRequest.*

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

    // Default levels for a newly-created logger (b/c ROOT)
    static final defaultLogLevels = [configuredLevel: NOT_SPECIFIED, effectiveLevel: INFO]

    // Some know loggers, internal to micronaut (not exhaustive)
    static final expectedBuiltinLoggers = ['io.micronaut', 'io.netty']

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

        then:
        result.containsKey 'loggers'

        and: 'we have all the loggers expected from configuration'
        configuredLoggers.each { log, _ ->
            assert result.loggers.containsKey(log)
            assert result.loggers[log] == configuredLoggers[log]
        }

        and: 'we have some expected builtin loggers'
        expectedBuiltinLoggers.every {
            result.loggers.containsKey it
        }
    }

    @Unroll
    void 'test that a configured logger "#name" can be retrieved by name from the endpoint'() {
        when:
        def response = client.exchange(GET("/loggers/${name}"), Map).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.body() == configuredLoggers[name] ?: defaultLogLevels

        where:
        name << ['journal', 'ROOT', 'errors']
    }

    @Unroll
    void 'test that log levels on logger "#name" can be configured via the loggers endpoint'() {
        given:
        def uri = "/loggers/${name}".toString()

        when:
        def response = client.exchange(GET(uri), Map).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.body() == configuredLoggers[name] ?: defaultLogLevels

        when: 'we request the log level on the logger is changed'
        response = client.exchange(POST(uri, [configuredLevel: level]))
                .blockingFirst()

        then: 'we get back success'
        response.status == HttpStatus.OK

        when: 'we again request info on the logger'
        response = client.exchange(GET(uri), Map).blockingFirst()

        then: 'we get back the newly configured level'
        response.status == HttpStatus.OK
        with (response.body()) {
            configuredLevel == expectedConfiguredLevel
            effectiveLevel == expectedEffectiveLevel
        }

        where:
        name        | level  | expectedConfiguredLevel | expectedEffectiveLevel
        'errors'    | null   | NOT_SPECIFIED           | INFO
        'whatever'  | WARN   | WARN                    | WARN
    }

    void 'test that an attempt to set log level without specifying the logger name will fail'() {
        given:
        def uri = '/loggers'

        when:
        client.toBlocking().exchange(POST(uri, [:]))

        then:
        def e = thrown(HttpClientResponseException)

        and: 'without the logger name, uri will match endpoint @Read/GET method, but attempting POST'
        e.response.status == HttpStatus.METHOD_NOT_ALLOWED
    }

    void 'test that an attempt to set a bad log level will fail'() {
        given:
        def uri = '/loggers/errors'

        when:
        client.toBlocking().exchange(POST(uri, [configuredLevel: 'FOO']))

        then:
        def e = thrown(HttpClientResponseException)

        and:
        e.response.status == HttpStatus.BAD_REQUEST
        e.message.contains 'Cannot deserialize value of type `io.micronaut.logging.LogLevel` from String "FOO"'
    }

    void 'test that an attempt to set ROOT logger to NOT_SPECIFIED level will fail'() {
        given:
        def uri = '/loggers/ROOT'

        when:
        client.toBlocking().exchange(POST(uri, [configuredLevel: NOT_SPECIFIED]))

        then:
        def e = thrown(HttpClientResponseException)

        and:
        e.response.status == HttpStatus.BAD_REQUEST
        e.message == 'Argument [LogLevel configuredLevel] not satisfied: Invalid log level specified: NOT_SPECIFIED'
    }

}
