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
package io.micronaut.inject.context

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Type
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Issue
import spock.lang.Specification

class RegisterSingletonSpec extends Specification {

    void "test register singleton method"() {
        given:
        BeanContext context = new DefaultBeanContext().start()
        def b = new B()

        when:
        context.registerSingleton(b)

        then:
        context.getBean(B, Qualifiers.byTypeArguments())
        context.getBean(B) == b
        b.a != null
        b.a == context.getBean(A)

        cleanup:
        context.close()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1851')
    void "test register singleton with type qualifier"() {
        when:
        def context = BeanContext.run()

        then:
        !context.findBean(DynamicService, Qualifiers.byTypeArguments(String)).present

        when:
        context.registerSingleton(DynamicService, new DefaultDynamicService<String>(String), Qualifiers.byTypeArguments(String))

        then:
        context.findBean(DynamicService, Qualifiers.byTypeArguments(String)).present
        context.findBean(DynamicService, Qualifiers.byTypeArguments(String)).get() instanceof DefaultDynamicService
        context.findBean(DynamicService, Qualifiers.byTypeArguments(String)).get().type == String

        and:
        !context.findBean(DynamicService, Qualifiers.byTypeArguments(Long)).present

        when:
        context.registerSingleton(DynamicService, new DefaultDynamicService<Long>(Long), Qualifiers.byTypeArguments(Long))

        then:
        context.findBean(DynamicService, Qualifiers.byTypeArguments(Long)).present
        context.findBean(DynamicService, Qualifiers.byTypeArguments(Long)).get() instanceof DefaultDynamicService
        context.findBean(DynamicService, Qualifiers.byTypeArguments(Long)).get().type == Long

        cleanup:
        context.close()
    }

    static interface DynamicService<T> {}

    @Type(String)
    static class DefaultDynamicService<T> implements DynamicService<T> {
        final Class<T> type

        DefaultDynamicService(Class<T> type) {
            this.type = type
        }
    }
}
