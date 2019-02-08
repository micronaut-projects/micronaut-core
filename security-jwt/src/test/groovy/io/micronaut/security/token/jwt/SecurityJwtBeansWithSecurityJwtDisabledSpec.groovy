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
package io.micronaut.security.token.jwt

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.jwt.bearer.AccessRefreshTokenLoginHandler
import io.micronaut.security.token.jwt.bearer.BearerTokenConfigurationProperties
import io.micronaut.security.token.jwt.bearer.BearerTokenReader
import io.micronaut.security.token.jwt.config.JwtConfigurationProperties
import io.micronaut.security.token.jwt.converters.EncryptionMethodConverter
import io.micronaut.security.token.jwt.converters.JWEAlgorithmConverter
import io.micronaut.security.token.jwt.converters.JWSAlgorithmConverter
import io.micronaut.security.token.jwt.cookie.JwtCookieClearerLogoutHandler
import io.micronaut.security.token.jwt.cookie.JwtCookieConfigurationProperties
import io.micronaut.security.token.jwt.cookie.JwtCookieLoginHandler
import io.micronaut.security.token.jwt.cookie.JwtCookieTokenReader
import io.micronaut.security.token.jwt.encryption.ec.ECEncryptionFactory
import io.micronaut.security.token.jwt.encryption.rsa.RSAEncryptionFactory
import io.micronaut.security.token.jwt.encryption.secret.SecretEncryptionConfiguration
import io.micronaut.security.token.jwt.encryption.secret.SecretEncryptionFactory
import io.micronaut.security.token.jwt.endpoints.OauthController
import io.micronaut.security.token.jwt.endpoints.OauthControllerConfigurationProperties
import io.micronaut.security.token.jwt.generator.AccessRefreshTokenGenerator
import io.micronaut.security.token.jwt.generator.JwtGeneratorConfigurationProperties
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.security.token.jwt.generator.claims.JWTClaimsSetGenerator
import io.micronaut.security.token.jwt.render.BearerTokenRenderer
import io.micronaut.security.token.jwt.signature.ec.ECSignatureFactory
import io.micronaut.security.token.jwt.signature.ec.ECSignatureGeneratorFactory
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureFactory
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGeneratorFactory
import io.micronaut.security.token.jwt.signature.secret.SecretSignatureFactory
import io.micronaut.security.token.jwt.validator.JwtTokenValidator
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class SecurityJwtBeansWithSecurityJwtDisabledSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                 : SecurityJwtBeansWithSecurityJwtDisabledSpec.simpleName,
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': false,
    ], Environment.TEST)

    @Unroll("if micronaut.security.enabled=true and micronaut.security.token.jwt.enabled=false bean [#description] is not loaded")
    void "if micronaut.security.enabled=false security related beans are not loaded"(Class clazz, String description) {
        when:
        embeddedServer.applicationContext.getBean(clazz)

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type ['+clazz.name+'] exists.')

        where:
        clazz << [
                AccessRefreshTokenLoginHandler,
                BearerTokenConfigurationProperties,
                BearerTokenReader,
                JwtConfigurationProperties,
                EncryptionMethodConverter,
                JWEAlgorithmConverter,
                JWSAlgorithmConverter,
                JwtCookieClearerLogoutHandler,
                JwtCookieConfigurationProperties,
                JwtCookieLoginHandler,
                JwtCookieTokenReader,
                ECEncryptionFactory,
                RSAEncryptionFactory,
                SecretEncryptionConfiguration,
                SecretEncryptionFactory,
                OauthController,
                OauthControllerConfigurationProperties,
                JWTClaimsSetGenerator,
                AccessRefreshTokenGenerator,
                JwtGeneratorConfigurationProperties,
                JwtTokenGenerator,
                BearerTokenRenderer,
                ECSignatureFactory,
                ECSignatureGeneratorFactory,
                RSASignatureFactory,
                RSASignatureGeneratorFactory,
                SecretSignatureFactory,
                JwtTokenValidator,
        ]

        description = clazz.name
    }
}
