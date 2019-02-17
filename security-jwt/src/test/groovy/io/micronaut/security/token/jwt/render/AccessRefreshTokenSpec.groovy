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
package io.micronaut.security.token.jwt.render

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

import static com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY

class AccessRefreshTokenSpec extends Specification {
    def "token json matches OAuth 2.0 RFC6749 specification"(){
        given: "we have an jackson mapper that will give us consistent results"
            ObjectMapper mapper = new ObjectMapper()
            mapper.configure(SORT_PROPERTIES_ALPHABETICALLY, true)

        and : "a fully populated token"
            AccessRefreshToken token = new AccessRefreshToken("1234", "abcd", "Bearer")

        when: "we serialize the object to json"
            def rawJsonString = mapper.writeValueAsString(token)

        then: "we will get an OAuth 2.0 RFC6749 compliant value"
            rawJsonString == "{\"access_token\":\"1234\",\"refresh_token\":\"abcd\",\"token_type\":\"Bearer\"}"
    }
}
