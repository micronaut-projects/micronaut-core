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
package io.micronaut.inject.configurations

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.configurations.requiresproperty.RequiresProperty

class RequiresBeanCompileSpec extends AbstractTypeElementSpec {

    void "test requires property when not present"() {
        when:
            ApplicationContext context = buildContext("""
@Configuration
@Requires(property = "data-source.url")
package test;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
""", "Test",
                    """
package test;
import jakarta.inject.Singleton;

@Singleton
class RequiresProperty {
}

""")

        then:
        !context.containsBean(RequiresProperty)

        when:
        context.getBean(RequiresProperty)

        then:
        NoSuchBeanException e = thrown()
        def list = e.message.readLines().toList()
        list[0] == 'No bean of type [io.micronaut.inject.configurations.requiresproperty.RequiresProperty] exists. '
        list[1] == '* [RequiresProperty] is disabled because it is within the package [io.micronaut.inject.configurations.requiresproperty] which is disabled due to bean requirements: '
        list[2] == ' - Required property [data-source.url] not present'

        cleanup:
        context.close()
    }
}
