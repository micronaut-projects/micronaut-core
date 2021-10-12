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
package io.micronaut.inject.foreach

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.MapPropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class EachPropertyNullableSpec extends Specification {

    void 'test nullable dependencies with EachProperties'() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext(Environment.TEST)
        applicationContext.environment.addPropertySource(MapPropertySource.of(
            Environment.TEST,
            [
                'foo.bar.one.url'            : 'jdbc:h2:mem:flywayDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
                'someconf.one.foo'               : true,
                'someconf.two.foo'               : false
            ]
        ))

        applicationContext.start()

        when:
        SomeConfiguration bean = applicationContext.getBean(SomeConfiguration, Qualifiers.byName("one"))

        then:
        noExceptionThrown()
        bean.getNameQualifier() == 'one'
        bean.getOtherConfig() != null

        when:
        SomeConfiguration bean2 = applicationContext.getBean(SomeConfiguration, Qualifiers.byName("two"))

        then:
        noExceptionThrown()
        bean2.getNameQualifier() == 'two'
        bean2.getOtherConfig() == null

        cleanup:
        applicationContext.close()
    }
}
