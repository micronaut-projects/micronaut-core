/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture

/**
 * Verifies that a {@link ReadTimeoutException} raised for a blocking {@code @Client} method carries
 * a stack trace pointing at the method invocation (see gh-12655), rather than at static
 * initialization of a shared singleton, and that concurrent timeouts produce independent instances.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/12655")
class ReadTimeoutStackTraceSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer http1Server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                         : 'ReadTimeoutStackTraceSpec',
            'micronaut.http.client.read-timeout': '1s'
    ])

    @Shared
    @AutoCleanup
    EmbeddedServer http2Server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                                                 : 'ReadTimeoutStackTraceSpec',
            'micronaut.server.http-version'                             : 'HTTP_2_0',
            'micronaut.server.ssl.enabled'                              : true,
            'micronaut.server.ssl.build-self-signed'                    : true,
            'micronaut.http.client.http-version'                        : 'HTTP_2_0',
            'micronaut.http.client.ssl.insecure-trust-all-certificates' : true,
            'micronaut.http.client.read-timeout'                        : '1s'
    ])

    @Unroll
    void "read timeout stack trace points to the @Client invocation over #protocol"() {
        given:
        SlowClient slowClient = server.applicationContext.getBean(SlowClient)

        when:
        slowClient.slow()

        then:
        ReadTimeoutException e = thrown(ReadTimeoutException)
        // The exception must NOT be the deprecated shared singleton.
        !e.is(ReadTimeoutException.TIMEOUT_EXCEPTION)
        // The stack trace must mention the code that made the call, not only Netty/event-loop frames.
        e.stackTrace.any { it.className.contains('ReadTimeoutStackTraceSpec') }
        e.stackTrace.any { it.className.contains('SlowClient') }
        // The original execution stack (where the failure was constructed) is preserved for debugging.
        e.suppressed.any { it.message?.contains('background thread') }

        where:
        protocol   | server
        'HTTP/1.1' | http1Server
        'HTTP/2'   | http2Server
    }

    @Unroll
    void "concurrent read timeouts produce independent exceptions over #protocol"() {
        given:
        SlowClient slowClient = server.applicationContext.getBean(SlowClient)

        when: "two blocking calls time out concurrently"
        def futures = (1..2).collect { i ->
            CompletableFuture.supplyAsync {
                try {
                    slowClient.slow()
                    return null
                } catch (ReadTimeoutException ex) {
                    return ex
                }
            }
        }
        List<ReadTimeoutException> exceptions = futures.collect { it.get() }

        then: "each call got its own exception instance with its own caller stack"
        exceptions.size() == 2
        exceptions.every { it != null }
        !exceptions[0].is(exceptions[1])
        exceptions.every { !it.is(ReadTimeoutException.TIMEOUT_EXCEPTION) }
        exceptions.every { ex -> ex.stackTrace.any { it.className.contains('ReadTimeoutStackTraceSpec') } }

        where:
        protocol   | server
        'HTTP/1.1' | http1Server
        'HTTP/2'   | http2Server
    }

    @Requires(property = 'spec.name', value = 'ReadTimeoutStackTraceSpec')
    @Controller('/timeout-stack')
    static class SlowController {

        @Get(value = '/slow', produces = MediaType.TEXT_PLAIN)
        String slow() {
            sleep 5000
            return 'ok'
        }
    }

    @Requires(property = 'spec.name', value = 'ReadTimeoutStackTraceSpec')
    @Client('/timeout-stack')
    @Consumes(MediaType.TEXT_PLAIN)
    static interface SlowClient {

        @Get('/slow')
        String slow()
    }
}
