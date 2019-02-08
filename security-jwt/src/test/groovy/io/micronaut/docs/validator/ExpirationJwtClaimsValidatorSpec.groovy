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
package io.micronaut.docs.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.jwt.validator.ExpirationJwtClaimsValidator
import spock.lang.Specification

class ExpirationJwtClaimsValidatorSpec extends Specification {

    void "by default ExpirationJwtClaimsValidator is enabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : ExpirationJwtClaimsValidatorSpec.simpleName,
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
        ], Environment.TEST)

        when:
        embeddedServer.applicationContext.getBean(ExpirationJwtClaimsValidator)

        then:
        noExceptionThrown()

        cleanup:
        embeddedServer.close()
    }


    void "you can disable ExpirationJwtClaimsValidator if you set micronaut.security.token.jwt.claims-validators.expiration=false"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : ExpirationJwtClaimsValidatorSpec.simpleName,
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
                'micronaut.security.token.jwt.claims-validators.expiration': false
        ], Environment.TEST)

        when:
        embeddedServer.applicationContext.getBean(ExpirationJwtClaimsValidator)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        embeddedServer.close()
    }
}
