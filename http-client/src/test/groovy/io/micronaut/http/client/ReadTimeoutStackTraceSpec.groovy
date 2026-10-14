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
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Stack traces of exceptions thrown from blocking client calls, see gh-12655. With
 * {@code micronaut.http.client.blocking-caller-stack-trace} enabled, the stack trace points at the
 * code that made the call and the original (event loop) stack trace is kept as a suppressed
 * exception. It is disabled by default, which keeps the previous stack traces.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/12655")
class ReadTimeoutStackTraceSpec extends Specification {

    private static final String EXECUTION_TRACE = 'BlockingClientExecutionTrace'
    private static final String BLOCKING_EXCHANGE = 'io.micronaut.http.client.netty.NettyHttpClient$1'

    @Shared
    @AutoCleanup
    EmbeddedServer http1Server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ReadTimeoutStackTraceSpec'
    ])

    @Shared
    @AutoCleanup
    EmbeddedServer http2Server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                             : 'ReadTimeoutStackTraceSpec',
            'micronaut.server.http-version'         : 'HTTP_2_0',
            'micronaut.server.ssl.enabled'          : true,
            'micronaut.server.ssl.build-self-signed': true,
            'micronaut.server.ssl.port'             : -1
    ])

    @AutoCleanup
    ApplicationContext clientContext

    void "blocking-caller-stack-trace is disabled by default"() {
        expect:
        !new DefaultHttpClientConfiguration().blockingCallerStackTrace
        !HttpClientConfiguration.DEFAULT_BLOCKING_CALLER_STACK_TRACE
    }

    void "blocking-caller-stack-trace is bound from configuration"() {
        given:
        clientContext = ApplicationContext.run('micronaut.http.client.blocking-caller-stack-trace': true)

        expect:
        clientContext.getBean(DefaultHttpClientConfiguration).blockingCallerStackTrace
    }

    void "read timeout stack trace points to the @Client invocation over #protocol when enabled"() {
        given:
        SlowClient slowClient = slowClient(server, true)

        when:
        slowClient.slow()

        then:
        ReadTimeoutException e = thrown()
        !e.is(ReadTimeoutException.TIMEOUT_EXCEPTION)
        !e.headersReceived
        e.message.endsWith('Read Timeout')

        and: 'the stack trace starts at the blocking client and runs through the caller'
        e.stackTrace[0].className == BLOCKING_EXCHANGE
        e.stackTrace[0].methodName == 'exchange'
        indexOf(e.stackTrace, 'SlowClient') >= 0
        e.stackTrace.findIndexOf { it.className == ReadTimeoutStackTraceSpec.name } > indexOf(e.stackTrace, 'SlowClient')
        e.stackTrace.find { it.className == ReadTimeoutStackTraceSpec.name }.methodName.startsWith('$spock_feature')

        and: 'no event loop frames are left in the stack trace'
        !e.stackTrace.any { it.className.startsWith('io.netty.') }

        and: 'the original stack trace is kept as a suppressed exception'
        Throwable trace = executionTrace(e)
        trace != null
        trace.message.contains('background thread')
        trace.stackTrace.any { it.className.startsWith('io.netty.') || it.className.startsWith('io.micronaut.http.client.netty.') }
        !trace.stackTrace.any { it.className == ReadTimeoutStackTraceSpec.name }

        where:
        protocol   | server
        'HTTP/1.1' | http1Server
        'HTTP/2'   | http2Server
    }

    void "printed stack trace shows the caller and the original trace over #protocol when enabled"() {
        given:
        SlowClient slowClient = slowClient(server, true)

        when:
        slowClient.slow()

        then:
        ReadTimeoutException e = thrown()
        String printed = print(e)
        List<String> lines = printed.readLines()

        and: 'the first frame is the blocking client, followed by the declarative client and the caller'
        lines[0].startsWith(ReadTimeoutException.name)
        lines[0].endsWith('Read Timeout')
        lines[1].trim().startsWith("at ${BLOCKING_EXCHANGE}.exchange(")
        printed.contains('SlowClient$Intercepted.slow(')
        printed.contains("at ${ReadTimeoutStackTraceSpec.name}.\$spock_feature")
        printed.indexOf('SlowClient$Intercepted.slow(') < printed.indexOf("at ${ReadTimeoutStackTraceSpec.name}.")

        and: 'the original stack trace is printed as a suppressed exception, after the caller'
        printed.contains("Suppressed: ${NettyHttpClientExecutionTraceName()}: Client request execution failed on a background thread")
        printed.indexOf('Suppressed: ' + NettyHttpClientExecutionTraceName()) > printed.indexOf("at ${ReadTimeoutStackTraceSpec.name}.")

        where:
        protocol   | server
        'HTTP/1.1' | http1Server
        'HTTP/2'   | http2Server
    }

    void "read timeout stack trace is unchanged over #protocol when disabled"() {
        given:
        SlowClient slowClient = slowClient(server, enabled)

        when:
        slowClient.slow()

        then:
        ReadTimeoutException e = thrown()
        !e.is(ReadTimeoutException.TIMEOUT_EXCEPTION)

        and: 'the stack trace is the one of the thread that constructed the exception'
        e.stackTrace[0].className != BLOCKING_EXCHANGE
        !e.stackTrace.any { it.className == ReadTimeoutStackTraceSpec.name }
        executionTrace(e) == null
        !print(e).contains(EXECUTION_TRACE)

        where:
        protocol   | server      | enabled
        'HTTP/1.1' | http1Server | null
        'HTTP/1.1' | http1Server | false
        'HTTP/2'   | http2Server | null
        'HTTP/2'   | http2Server | false
    }

    void "error response stack trace points to the caller over #protocol when enabled"() {
        given:
        SlowClient slowClient = slowClient(server, true)

        when:
        slowClient.error()

        then:
        HttpClientResponseException e = thrown()
        e.status.code == 500
        e.stackTrace[0].className == BLOCKING_EXCHANGE
        indexOf(e.stackTrace, 'SlowClient') >= 0
        e.stackTrace.any { it.className == ReadTimeoutStackTraceSpec.name }
        executionTrace(e) != null

        where:
        protocol   | server
        'HTTP/1.1' | http1Server
        'HTTP/2'   | http2Server
    }

    void "low-level blocking client stack trace points to the caller over #protocol when enabled"() {
        given:
        slowClient(server, true)
        HttpClient client = clientContext.createBean(HttpClient, server.URL)

        when:
        client.toBlocking().retrieve(HttpRequest.GET('/timeout-stack/slow'), String)

        then:
        ReadTimeoutException e = thrown()
        e.stackTrace[0].className == BLOCKING_EXCHANGE
        e.stackTrace.any { it.className.contains('BlockingHttpClient') || it.methodName == 'retrieve' }
        e.stackTrace.any { it.className == ReadTimeoutStackTraceSpec.name }
        executionTrace(e) != null

        cleanup:
        client?.close()

        where:
        protocol   | server
        'HTTP/1.1' | http1Server
        'HTTP/2'   | http2Server
    }

    void "a successful blocking call is not affected over #protocol when enabled"() {
        given:
        SlowClient slowClient = slowClient(server, true)

        expect:
        slowClient.fast() == 'ok'

        where:
        protocol   | server
        'HTTP/1.1' | http1Server
        'HTTP/2'   | http2Server
    }

    void "concurrent read timeouts produce independent exceptions with their own caller stack over #protocol"() {
        given:
        SlowClient slowClient = slowClient(server, true)

        when: "two blocking calls time out concurrently"
        List<ReadTimeoutException> exceptions = (1..2).collect {
            CompletableFuture.supplyAsync {
                try {
                    slowClient.slow()
                    return null
                } catch (ReadTimeoutException ex) {
                    return ex
                }
            }
        }.collect { it.get() }

        then: "each call got its own exception instance, stack trace and suppressed trace"
        exceptions.size() == 2
        exceptions.every { it != null }
        !exceptions[0].is(exceptions[1])
        exceptions.every { !it.is(ReadTimeoutException.TIMEOUT_EXCEPTION) }
        exceptions.every { ex -> ex.stackTrace.any { it.className.startsWith(ReadTimeoutStackTraceSpec.name) } }
        exceptions.every { ex -> ex.suppressed.count { it.class.simpleName == EXECUTION_TRACE } == 1 }
        !executionTrace(exceptions[0]).is(executionTrace(exceptions[1]))

        where:
        protocol   | server
        'HTTP/1.1' | http1Server
        'HTTP/2'   | http2Server
    }

    private SlowClient slowClient(EmbeddedServer server, Boolean callerStackTrace) {
        Map<String, Object> config = [
                'spec.name'                                                : 'ReadTimeoutStackTraceSpec',
                'spec.client'                                              : true,
                'spec.url'                                                 : server.URL.toString(),
                'micronaut.http.client.read-timeout'                       : '1s',
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true
        ]
        if (server.is(http2Server)) {
            config['micronaut.http.client.http-version'] = 'HTTP_2_0'
        }
        if (callerStackTrace != null) {
            config['micronaut.http.client.blocking-caller-stack-trace'] = callerStackTrace
        }
        clientContext = ApplicationContext.run(config)
        return clientContext.getBean(SlowClient)
    }

    private static int indexOf(StackTraceElement[] stackTrace, String className) {
        return stackTrace.findIndexOf { it.className.contains(className) }
    }

    private static Throwable executionTrace(Throwable e) {
        return e.suppressed.find { it.class.simpleName == EXECUTION_TRACE }
    }

    private static String NettyHttpClientExecutionTraceName() {
        return 'io.micronaut.http.client.netty.NettyHttpClient$' + EXECUTION_TRACE
    }

    private static String print(Throwable e) {
        StringWriter writer = new StringWriter()
        e.printStackTrace(new PrintWriter(writer))
        return writer.toString()
    }

    @Requires(property = 'spec.name', value = 'ReadTimeoutStackTraceSpec')
    @Requires(missingProperty = 'spec.client')
    @Controller('/timeout-stack')
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class SlowController {

        @Get(value = '/slow', produces = MediaType.TEXT_PLAIN)
        String slow() {
            sleep 5000
            return 'ok'
        }

        @Get(value = '/fast', produces = MediaType.TEXT_PLAIN)
        String fast() {
            return 'ok'
        }

        @Get(value = '/error', produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> error() {
            return HttpResponse.serverError('boom')
        }
    }

    @Requires(property = 'spec.name', value = 'ReadTimeoutStackTraceSpec')
    @Requires(property = 'spec.client')
    @Client('${spec.url}/timeout-stack')
    @Consumes(MediaType.TEXT_PLAIN)
    static interface SlowClient {

        @Get('/slow')
        String slow()

        @Get('/fast')
        String fast()

        @Get('/error')
        String error()
    }
}
