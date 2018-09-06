package io.micronaut.security.propagation

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.security.filters.SecurityFilter
import io.micronaut.security.token.propagation.TokenPropagationConfiguration
import io.micronaut.security.token.propagation.TokenPropagationHttpClientFilter
import io.micronaut.security.token.writer.TokenWriter
import spock.lang.Specification
import spock.lang.Unroll

class TokenPropagationHttpClientFilterSpec extends Specification {

    void "if current request attribute TOKEN contains a token, it gets written to target request"() {
        given:
        String sampleJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c'
        TokenWriter tokenWriter = Mock(TokenWriter)
        TokenPropagationHttpClientFilter clientFilter = new TokenPropagationHttpClientFilter(tokenWriter,null)
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
        TokenPropagationHttpClientFilter clientFilter = new TokenPropagationHttpClientFilter(tokenWriter,null)
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

    @Unroll("for a request with service #serviceId uri #uri given: serviceregex: #serviceIdRegex uriRegex: #uriRegex #description")
    void "test shouldProcessRequest"(boolean expected,
                                     String serviceId,
                                     String uri,
                                     String serviceIdRegex,
                                     String uriRegex,
                                     String description) {
        when:
        def configuration = Stub(TokenPropagationConfiguration) {
            getServicesRegex() >> serviceIdRegex
            getUriRegex() >> uriRegex
        }
        TokenPropagationHttpClientFilter clientFilter = new TokenPropagationHttpClientFilter(null, configuration)

        then:
        clientFilter.shouldProcessRequest(Optional.of(serviceId), uri) == expected

        where:
        expected || serviceId                             | uri             | serviceIdRegex           | uriRegex
        true     || 'https://mymicroservice.micronaut.io' | '/api/books'    | 'https://.*.micronaut.io'| null
        false    || 'https://google.com'                  | '/api/books'    | 'https://.*.micronaut.io'| null
        true     || 'https://mymicroservice.micronaut.io' | '/api/books'    | null                     | '^.*books$'
        false    || 'https://mymicroservice.micronaut.io' | '/api/invoices' | null                     | '^.*books$'


        description = expected ? 'should be processed' : ' should NOT be processed'
    }
}
