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
            AccessRefreshToken token = new AccessRefreshToken("1234", "abcd")

        when: "we serialize the object to json"
            def rawJsonString = mapper.writeValueAsString(token)

        then: "we will get an OAuth 2.0 RFC6749 compliant value"
            rawJsonString == "{\"access_token\":\"1234\",\"refresh_token\":\"abcd\"}"
    }
}
