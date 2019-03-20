package io.micronaut.security.token.jwt

trait ConfigurationFixture {

    Map<String, Object> minimumConfig = [
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
    ]

}