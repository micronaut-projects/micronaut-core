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
import io.micronaut.security.token.jwt.validator.SubjectNotNullJwtClaimsValidator
import spock.lang.Specification

class SubjectNotNullJwtClaimsValidatorSpec extends Specification {

    void "by default SubjectNotNullJwtClaimsValidator is enabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : SubjectNotNullJwtClaimsValidatorSpec.simpleName,
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true

        ], Environment.TEST)

        when:
        embeddedServer.applicationContext.getBean(SubjectNotNullJwtClaimsValidator)

        then:
        noExceptionThrown()

        cleanup:
        embeddedServer.close()
    }


    void "you can disable SubjectNotNullJwtClaimsValidator if you set micronaut.security.token.jwt.claims-validators.subject=false"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : SubjectNotNullJwtClaimsValidatorSpec.simpleName,
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
                'micronaut.security.token.jwt.claims-validators.subject-not-null': false

        ], Environment.TEST)

        when:
        embeddedServer.applicationContext.getBean(SubjectNotNullJwtClaimsValidator)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        embeddedServer.close()
    }
}
