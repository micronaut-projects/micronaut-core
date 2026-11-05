package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.regex.Pattern

class JdkBlockingHttpClientThreadLeakSpec extends Specification {

    private static final Pattern JDK_HTTP_CLIENT_SELECTOR = Pattern.compile(/HttpClient-\d+-SelectorManager/)

    @Shared @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        'spec.name': 'JdkBlockingHttpClientThreadLeakSpec'
    ])

    @Shared @AutoCleanup
    ApplicationContext clientCtx = ApplicationContext.run([
        'spec.name': 'JdkBlockingHttpClientThreadLeakSpec',
        'micronaut.http.services.leakspec.url': server.URL.toString()
    ])

    def "synchronous declarative @Client does not spawn a new JDK HttpClient per call"() {
        given:
        LeakClient client = clientCtx.getBean(LeakClient)
        int baseline = countSelectorThreads()

        when:
        500.times { client.ping() }

        then: 'thread count is measured immediately, before GC can reap leaked clients'
        int after = countSelectorThreads()
        (after - baseline) <= 1
    }

    private static int countSelectorThreads() {
        Thread.getAllStackTraces().keySet().count { JDK_HTTP_CLIENT_SELECTOR.matcher(it.name).matches() } as int
    }

    @Requires(property = 'spec.name', value = 'JdkBlockingHttpClientThreadLeakSpec')
    @Client('leakspec')
    static interface LeakClient {
        @Get('/leak/ping')
        String ping()
    }

    @Requires(property = 'spec.name', value = 'JdkBlockingHttpClientThreadLeakSpec')
    @Controller('/leak')
    static class LeakController {
        @Get('/ping')
        String ping() { 'pong' }
    }
}
