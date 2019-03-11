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
package io.micronaut.security

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.AuthenticationArgumentBinder
import io.micronaut.security.authentication.AuthenticationExceptionHandler
import io.micronaut.security.authentication.PrincipalArgumentBinder
import io.micronaut.security.config.InterceptUrlMapConverter
import io.micronaut.security.config.SecurityConfigurationProperties
import io.micronaut.security.endpoints.LoginController
import io.micronaut.security.endpoints.LoginControllerConfigurationProperties
import io.micronaut.security.endpoints.LogoutController
import io.micronaut.security.endpoints.LogoutControllerConfigurationProperties
import io.micronaut.security.filters.SecurityFilter
import io.micronaut.security.handlers.HttpStatusCodeRejectionHandler
import io.micronaut.security.rules.ConfigurationInterceptUrlMapRule
import io.micronaut.security.rules.IpPatternsRule
import io.micronaut.security.rules.SecuredAnnotationRule
import io.micronaut.security.rules.SensitiveEndpointRule
import io.micronaut.security.token.TokenAuthenticationFetcher
import io.micronaut.security.token.basicauth.BasicAuthTokenReader
import io.micronaut.security.token.basicauth.BasicAuthTokenReaderConfigurationProperties
import io.micronaut.security.token.basicauth.BasicAuthTokenValidator
import io.micronaut.security.token.config.TokenConfigurationProperties
import io.micronaut.security.token.propagation.TokenPropagationConfigurationProperties
import io.micronaut.security.token.propagation.TokenPropagationHttpClientFilter
import io.micronaut.security.token.writer.HttpHeaderTokenWriter
import io.micronaut.security.token.writer.HttpHeaderTokenWriterConfigurationProperties
import io.micronaut.security.utils.DefaultSecurityService
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class SecurityDisabledSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                 : SecurityDisabledSpec.simpleName,
            'micronaut.security.enabled': false,
    ], Environment.TEST)

    @Unroll("if micronaut.security.enabled=false bean [#description] is not loaded")
    void "if micronaut.security.enabled=false security related beans are not loaded"(Class clazz, String description) {
        when:
        embeddedServer.applicationContext.getBean(clazz)

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type ['+clazz.name+'] exists.')

        where:
        clazz << [
                SecurityFilter,
                AuthenticationArgumentBinder,
                AuthenticationExceptionHandler,
                Authenticator,
                PrincipalArgumentBinder,
                InterceptUrlMapConverter,
                SecurityConfigurationProperties,
                LoginController,
                LoginControllerConfigurationProperties,
                LogoutController,
                LogoutControllerConfigurationProperties,
                HttpStatusCodeRejectionHandler,
                ConfigurationInterceptUrlMapRule,
                IpPatternsRule,
                SecuredAnnotationRule,
                SensitiveEndpointRule,
                BasicAuthTokenReader,
                BasicAuthTokenReaderConfigurationProperties,
                BasicAuthTokenValidator,
                TokenConfigurationProperties,
                TokenPropagationConfigurationProperties,
                TokenPropagationHttpClientFilter,
                HttpHeaderTokenWriter,
                HttpHeaderTokenWriterConfigurationProperties,
                TokenAuthenticationFetcher,
                DefaultSecurityService,

        ]

        description = clazz.name
    }
}