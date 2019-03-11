/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
