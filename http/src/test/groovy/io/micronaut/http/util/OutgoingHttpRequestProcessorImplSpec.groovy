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
package io.micronaut.http.util

import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Pattern

class OutgoingHttpRequestProcessorImplSpec extends Specification {
    @Unroll("for a request with service #serviceId uri #uri given: serviceregex: #serviceIdRegex uriRegex: #uriRegex #description")
    void "test shouldProcessRequest"(boolean expected,
                                     String serviceId,
                                     String uri,
                                     String serviceIdRegex,
                                     String uriRegex,
                                     String description) {
        when:
        def requestProcessorMatcher = new MockOutgoingRequestProcessorMatcher(serviceIdRegex, uriRegex)
        OutgoingHttpRequestProcessor requestProcessor = new OutgoingHttpRequestProcessorImpl()

        then:
        requestProcessor.shouldProcessRequest(requestProcessorMatcher, serviceId, uri) == expected

        where:
        expected || serviceId                             | uri             | serviceIdRegex           | uriRegex
        true     || 'https://mymicroservice.micronaut.io' | '/api/books'    | 'https://.*.micronaut.io'| null
        false    || 'https://google.com'                  | '/api/books'    | 'https://.*.micronaut.io'| null
        true     || 'https://mymicroservice.micronaut.io' | '/api/books'    | null                     | '^.*books$'
        true     || null                                  | '/api/books'    | null                     | '^.*books$'
        false    || 'https://mymicroservice.micronaut.io' | '/api/invoices' | null                     | '^.*books$'

        description = expected ? 'should be processed' : ' should NOT be processed'
    }
}

class MockOutgoingRequestProcessorMatcher implements OutgoingRequestProcessorMatcher {
    Pattern serviceIdPattern
    Pattern uriPattern

    MockOutgoingRequestProcessorMatcher(String serviceIdRegex, String uriRegex) {
        if (serviceIdRegex != null ) {
            serviceIdPattern = Pattern.compile(serviceIdRegex)
        }
        if (uriRegex !=null ) {
            uriPattern = Pattern.compile(uriRegex)
        }
    }

    @Override
    Pattern getServiceIdPattern() {
        return serviceIdPattern
    }

    @Override
    Pattern getUriPattern() {
        return uriPattern
    }
}

