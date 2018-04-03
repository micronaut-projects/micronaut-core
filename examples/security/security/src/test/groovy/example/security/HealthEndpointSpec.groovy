package example.security

import io.micronaut.context.ApplicationContext
import io.micronaut.http.*
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.UsernamePassword
import io.micronaut.security.jwt.AccessRefreshToken
import io.micronaut.security.jwt.DefaultAccessRefreshToken
import io.micronaut.security.jwt.TokenRefreshRequest
import io.micronaut.security.jwt.TokenValidator
import org.pac4j.core.profile.jwt.JwtClaims
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HealthEndpointSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void '/health endpoint, which is configured with sensitive false, is allowed anonymously'() {
        when:
        HttpResponse rsp = client.toBlocking().exchange("/health")

        then:
        rsp.status.code == 200
    }
}