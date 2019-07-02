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
import io.micronaut.context.exceptions.NoSuchBeanException
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

        def s1 = new DefaultDynamicService<String>()
        context.registerSingleton(DynamicService, s1, Qualifiers.byType(String))

        then:
        context.getBean(DynamicService, Qualifiers.byType(String))
        context.getBean(DynamicService, Qualifiers.byTypeArguments(String))


        cleanup:
        context.close()
    }

    static interface DynamicService<T> {}

    @Type(String)
    static class DefaultDynamicService<T> implements DynamicService<T> {}
}
