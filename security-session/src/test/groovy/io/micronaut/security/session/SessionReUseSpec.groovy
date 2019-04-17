package io.micronaut.security.session

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.LoadBalancer
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.filters.SecurityFilter
import io.micronaut.session.Session
import io.micronaut.session.SessionStore
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.Specification

import javax.inject.Singleton

class SessionReUseSpec extends Specification {

    void "test the same session is reused through login/logout/login"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : SessionReUseSpec.simpleName,
                'micronaut.security.enabled': true,
                'micronaut.security.session.enabled': true,
                'micronaut.security.endpoints.login.enabled': true,
                'micronaut.security.endpoints.logout.enabled': true,
        ], Environment.TEST)
        ApplicationContext context = embeddedServer.applicationContext
        def config = new DefaultHttpClientConfiguration(followRedirects: false)
        BlockingHttpClient client = context.createBean(HttpClient,
                LoadBalancer.fixed(embeddedServer.getURL()), config, null).toBlocking()
        SessionStore<Session> sessionStore = context.getBean(SessionStore)

        when:
        HttpResponse response = client.exchange(HttpRequest.POST("/login", "username=sherlock&password=password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
        String cookieId = getCookieId(response)
        String sessionId = getSessionId(cookieId)
        Session session = sessionStore.findSession(sessionId).get().get()

        then:
        session != null
        session.get(SecurityFilter.AUTHENTICATION).isPresent()


        when:
        response = client.exchange(HttpRequest.POST("/logout","")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .cookie(Cookie.of("SESSION", cookieId)))
        String afterLogoutCookieId = getCookieId(response)
        String afterLogoutSessionId = getSessionId(afterLogoutCookieId)
        Session afterLogoutSession = sessionStore.findSession(sessionId).get().get()

        then:
        afterLogoutSessionId == sessionId
        afterLogoutSession.is(session)
        !afterLogoutSession.get(SecurityFilter.AUTHENTICATION).isPresent()

        when:
        response = client.exchange(HttpRequest.POST("/login", "username=sherlock&password=password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .cookie(Cookie.of("SESSION", afterLogoutCookieId)))
        String afterLoginSessionId = getSessionId(getCookieId(response))
        Session afterLoginSession = sessionStore.findSession(sessionId).get().get()

        then:
        afterLoginSessionId == sessionId
        afterLoginSession.is(session)
        afterLoginSession.get(SecurityFilter.AUTHENTICATION).isPresent()

        cleanup:
        context.close()
    }

    private String getSessionId(String cookieId) {
        new String(Base64.getDecoder().decode(cookieId))
    }

    private String getCookieId(HttpResponse response) {
        response.getHeaders().get("set-cookie")
                .split(";")
                .collectEntries {
                    def parts = it.split("=")
                    if (parts.length > 1) {
                        [(parts[0]): parts[1]]
                    } else {
                        [(parts[0]): null]
                    }
                }.get("SESSION")
    }


    @Singleton
    @Requires(property = "spec.name", value = "SessionReUseSpec")
    static class AuthenticationProviderUserPassword implements AuthenticationProvider  { // <2>
        @Override
        public Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
            if ( authenticationRequest.getIdentity().equals("sherlock") &&
                    authenticationRequest.getSecret().equals("password") ) {
                UserDetails userDetails = new UserDetails((String) authenticationRequest.getIdentity(), new ArrayList<>());
                return Flowable.just(userDetails);
            }
            return Flowable.just(new AuthenticationFailed());
        }
    }
}
