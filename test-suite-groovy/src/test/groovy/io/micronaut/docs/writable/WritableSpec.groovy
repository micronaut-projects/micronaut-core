package io.micronaut.docs.writable

import io.micronaut.context.ApplicationContext
import io.micronaut.core.version.SemanticVersion
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.Jvm

// fails due to https://issues.apache.org/jira/browse/GROOVY-10145
@Requires({
    SemanticVersion.isAtLeastMajorMinor(GroovySystem.version, 4, 0) ||
            !Jvm.current.isJava16Compatible()
})
class WritableSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient, embeddedServer.getURL())


    void "test render template"() {
        expect:
        client.toBlocking().retrieve('/template/welcome') == 'Dear Fred Flintstone. Nice to meet you.'
    }

    void "test the correct headers are applied"() {
        when:
        HttpResponse response = client.toBlocking().exchange('/template/welcome', String)

        then:
        response.getHeaders().contains("Date")
        response.getHeaders().contains("Content-Length")
    }

}
