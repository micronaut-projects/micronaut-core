package io.micronaut.security.endpoints

import spock.lang.Specification

class OauthControllerSpec extends Specification {

    def "verify validateTokenRefreshRequest"() {
        given:
        OauthController oauthController = new OauthController(null, null)

        expect:
        !oauthController.validateTokenRefreshRequest(new TokenRefreshRequest(grantType: null, refreshToken: "XXXX"))

        and:
        !oauthController.validateTokenRefreshRequest(new TokenRefreshRequest(grantType: 'foo', refreshToken: "XXXX"))

        and:
        !oauthController.validateTokenRefreshRequest(new TokenRefreshRequest(grantType: 'refresh_token', refreshToken: null))

        and:
        oauthController.validateTokenRefreshRequest(new TokenRefreshRequest(grantType: 'refresh_token', refreshToken: "XXXX"))
    }
}
