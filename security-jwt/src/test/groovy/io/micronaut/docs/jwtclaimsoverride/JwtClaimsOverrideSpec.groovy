package io.micronaut.docs.jwtclaimsoverride

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.jwt.render.AccessRefreshToken
import io.micronaut.security.token.jwt.validator.JwtTokenValidator
import io.micronaut.security.token.validator.TokenValidator
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JwtClaimsOverrideSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'jwtclaimsoverride',
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.signatures.secret.generator.secret': 'pleaseChangeThisSecretForANewOne'
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void 'customize JWT claims'() {
        when:
        HttpRequest request = HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePasswordCredentials('sherlock', 'elementary')) // <4>
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(request, AccessRefreshToken)

        then:
        rsp.status.code == 200
        rsp.body.isPresent()
        rsp.body.get().accessToken
        rsp.body.get().refreshToken

        when:
        String accessToken = rsp.body.get().accessToken
        Authentication authentication = Flowable.fromPublisher(tokenValidator.validateToken(accessToken)).blockingFirst()
        println authentication.getAttributes()

        then:
        authentication.getAttributes()
        authentication.getAttributes().containsKey('roles')
        authentication.getAttributes().containsKey('iss')
        authentication.getAttributes().containsKey('exp')
        authentication.getAttributes().containsKey('iat')
        authentication.getAttributes().containsKey('email')
    }

    TokenValidator getTokenValidator() {
        embeddedServer.applicationContext.getBean(JwtTokenValidator.class)
    }
}
