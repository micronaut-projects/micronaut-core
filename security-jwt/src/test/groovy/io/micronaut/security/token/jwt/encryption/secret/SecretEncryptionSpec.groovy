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
package io.micronaut.security.token.jwt.encryption.secret

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration
import spock.lang.Specification

class SecretEncryptionSpec extends Specification {

    void "SecretEncryption constructor does not raise exception if jwe algorithm and encryption method set are valid"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
                'micronaut.security.token.jwt.encryptions.secret.generator.secret': 'XXX',
                'micronaut.security.token.jwt.encryptions.secret.generator.jwe-algorithm': 'dir',
                'micronaut.security.token.jwt.encryptions.secret.generator.encryption-method': 'A128CBC-HS256',
        ], Environment.TEST)

        when:
        ctx.getBean(SecretEncryptionFactory)

        then:
        noExceptionThrown()

        when:
        ctx.getBean(SecretEncryptionConfiguration)

        then:
        noExceptionThrown()

        when:
        EncryptionConfiguration encryptionConfiguration = ctx.getBean(EncryptionConfiguration)

        then:
        noExceptionThrown()

        encryptionConfiguration instanceof SecretEncryption

        cleanup:
        ctx.close()
    }
}
