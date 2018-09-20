package io.micronaut.security.propagation

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.util.OutgoingHttpRequestProcessor
import io.micronaut.http.util.OutgoingHttpRequestProcessorImpl
import io.micronaut.security.filters.SecurityFilter
import io.micronaut.security.token.propagation.TokenPropagationHttpClientFilter
import io.micronaut.security.token.writer.TokenWriter
import spock.lang.Shared
import spock.lang.Specification

class TokenPropagationHttpClientFilterSpec extends Specification {

    @Shared
    OutgoingHttpRequestProcessor requestProcessor = new OutgoingHttpRequestProcessorImpl()

    void "if current request attribute TOKEN contains a token, it gets written to target request"() {
        given:
        String sampleJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c'
        TokenWriter tokenWriter = Mock(TokenWriter)
        TokenPropagationHttpClientFilter clientFilter = new TokenPropagationHttpClientFilter(tokenWriter,null,requestProcessor)
        MutableHttpRequest<?> targetRequest = Stub(MutableHttpRequest)
        ClientFilterChain chain = Mock(ClientFilterChain)
        HttpRequest<Object> currentRequest =  Stub(MutableHttpRequest) {
            getAttribute(SecurityFilter.TOKEN) >> Optional.of(sampleJwt)
        }

        when:
        clientFilter.doFilter(targetRequest, chain, currentRequest)

        then:
        1 * tokenWriter.writeToken(targetRequest, sampleJwt)
        1 * chain.proceed(targetRequest)
    }

    void "if current request attribute TOKEN does NOT contains a token, it is not written to target request, but request proceeds"() {
        given:
        TokenWriter tokenWriter = Mock(TokenWriter)
        TokenPropagationHttpClientFilter clientFilter = new TokenPropagationHttpClientFilter(tokenWriter,null, requestProcessor)
        MutableHttpRequest<?> targetRequest = Stub(MutableHttpRequest)
        ClientFilterChain chain = Mock(ClientFilterChain)
        HttpRequest<Object> currentRequest =  Stub(MutableHttpRequest) {
            getAttribute(SecurityFilter.TOKEN) >> Optional.empty()
        }

        when:
        clientFilter.doFilter(targetRequest, chain, currentRequest)

        then:
        1 * chain.proceed(targetRequest)
    }
}
