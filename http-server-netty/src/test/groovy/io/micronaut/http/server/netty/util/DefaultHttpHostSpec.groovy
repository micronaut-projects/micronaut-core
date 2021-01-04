package io.micronaut.http.server.netty.util

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.server.util.HttpHostResolver
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class DefaultHttpHostSpec extends Specification {

    void "test configured host header"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.host-resolution.host-header': 'MyHost'])
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "MyHost": ["abc"],
                    "Forwarded": ["host=\"overridden\";proto=overridden"],
                    "X-Forwarded-Host": ["overridden"],
                    "X-Forwarded-Proto": ["overridden"],
                    "X-Forwarded-Port": ["overridden"]
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "http://abc:8080"

        cleanup:
        server.close()
    }

    void "test configured host header with no value"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.host-resolution.host-header': 'MyHost'])
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "Forwarded": ["host=\"overridden\";proto=overridden"],
                    "X-Forwarded-Host": ["overridden"],
                    "X-Forwarded-Proto": ["overridden"],
                    "X-Forwarded-Port": ["overridden"]
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "http://localhost:8080"

        cleanup:
        server.close()
    }

    void "test configured host and scheme header"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.host-resolution.host-header': 'MyHost',
                'micronaut.server.host-resolution.protocol-header': 'MyProto'
        ])
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "MyHost": ["abc"],
                    "MyProto": ["https"],
                    "Forwarded": ["host=\"overridden\";proto=overridden"],
                    "X-Forwarded-Host": ["overridden"],
                    "X-Forwarded-Proto": ["overridden"],
                    "X-Forwarded-Port": ["overridden"]
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "https://abc:8080"

        cleanup:
        server.close()
    }

    void "test configured host and scheme header with no value"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.host-resolution.host-header': 'MyHost',
                'micronaut.server.host-resolution.protocol-header': 'MyProto'
        ])
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "Forwarded": ["host=\"overridden\";proto=overridden"],
                    "X-Forwarded-Host": ["overridden"],
                    "X-Forwarded-Proto": ["overridden"],
                    "X-Forwarded-Port": ["overridden"]
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "http://localhost:8080"

        cleanup:
        server.close()
    }

    void "test configured host and scheme and port header"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.host-resolution.host-header': 'MyHost',
                'micronaut.server.host-resolution.protocol-header': 'MyProto',
                'micronaut.server.host-resolution.port-header': 'MyPort'
        ])
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "MyHost": ["abc"],
                    "MyProto": ["https"],
                    "MyPort": ["9000"],
                    "Forwarded": ["host=\"overridden\";proto=overridden"],
                    "X-Forwarded-Host": ["overridden"],
                    "X-Forwarded-Proto": ["overridden"],
                    "X-Forwarded-Port": ["overridden"]
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "https://abc:9000"

        cleanup:
        server.close()
    }

    void "test configured host and scheme and missing port header"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.host-resolution.host-header': 'MyHost',
                'micronaut.server.host-resolution.protocol-header': 'MyProto',
                'micronaut.server.host-resolution.port-header': 'MyPort'
        ])
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "MyHost": ["abc"],
                    "MyProto": ["https"],
                    "Forwarded": ["host=\"overridden\";proto=overridden"],
                    "X-Forwarded-Host": ["overridden"],
                    "X-Forwarded-Proto": ["overridden"],
                    "X-Forwarded-Port": ["overridden"]
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then: "the port from the URI is not used"
        host == "https://abc"

        cleanup:
        server.close()
    }

    void "test port in configured host header"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.host-resolution.host-header': 'MyHost',
                'micronaut.server.host-resolution.protocol-header': 'MyProto',
                'micronaut.server.host-resolution.port-in-host': true,
        ])
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "MyHost": ["abc:9000"],
                    "MyProto": ["https"],
                    "Forwarded": ["host=\"overridden\";proto=overridden"],
                    "X-Forwarded-Host": ["overridden"],
                    "X-Forwarded-Proto": ["overridden"],
                    "X-Forwarded-Port": ["overridden"]

            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "https://abc:9000"

        cleanup:
        server.close()
    }

    void "test host retrieved from forwarded"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "Forwarded": ["for=\"34.202.241.227\", for=10.32.108.32",
                                  "host=\"micronaut\";proto=https"],
                    "X-Forwarded-Host": ["overridden"],
                    "X-Forwarded-Proto": ["overridden"],
                    "X-Forwarded-Port": ["overridden"]
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "https://micronaut"

        cleanup:
        server.close()
    }

    void "test host retrieved from x-forwarded"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "X-Forwarded-Host": ["micronaut"],
                    "X-Forwarded-Proto": ["https"],
                    "X-Forwarded-Port": ["9000"]
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "https://micronaut:9000"

        cleanup:
        server.close()
    }

    void "test host retrieved from x-forwarded with port in host"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "X-Forwarded-Host": ["micronaut:9000"],
                    "X-Forwarded-Proto": ["https"],
                    "X-Forwarded-Port": ["overridden"]
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "https://micronaut:9000"

        cleanup:
        server.close()
    }

    void "test host retrieved from host header"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "Host": ["micronaut"],
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "http://micronaut:8080"

        cleanup:
        server.close()
    }

    void "test host retrieved from host header with no URI"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "Host": ["micronaut"],
            ])
            getUri() >> new URI("/")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "http://micronaut"

        cleanup:
        server.close()
    }


    void "test host retrieved from host header with port"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "Host": ["micronaut:9000"],
            ])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "http://micronaut:9000"

        cleanup:
        server.close()
    }

    void "test host retrieved from the uri"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([:])
            getUri() >> new URI("http://localhost:8080")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "http://localhost:8080"

        cleanup:
        server.close()
    }

    void "test host retrieved from the embedded server"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([:])
            getUri() >> new URI("/")
        }

        when:
        String host = hostResolver.resolve(request)

        then:
        host == "http://localhost:${server.getPort()}"

        when:
        host = hostResolver.resolve(null)

        then:
        host == "http://localhost:${server.getPort()}"

        cleanup:
        server.close()
    }

    void "test allowed host validation"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['micronaut.server.host-resolution.allowed-hosts': hosts])
        HttpHostResolver hostResolver = server.applicationContext.getBean(HttpHostResolver)

        when:
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([
                    "Host": [host],
            ])
            getUri() >> new URI("http://localhost:8080")
        }
        String result = hostResolver.resolve(request)

        then:
        result == expected

        cleanup:
        server.close()

        where:
        host             | expected                | hosts
        "micronaut:9000" | "http://micronaut:9000" | ['http://micronaut:900\\d']
        "micronaut:9001" | "http://micronaut:9001" | ['http://micronaut:900\\d']
        "micronaut:900"  | "http://localhost"      | ['http://micronaut:900\\d']
        "micronaut:9005" | "http://localhost"      | ['http://micronaut:9000', 'http://micronaut:9001']
    }
}
