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
package io.micronaut.runtime.http.scope

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import io.micronaut.support.AbstractBeanDefinitionSpec
import jakarta.inject.Scope

/**
 * @author Marcel Overdijk
 * @since 1.2.0
 */
class RequestScopeSpec extends AbstractBeanDefinitionSpec {

    void "test parse bean definition data"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.RequestBean", """
package test;

import io.micronaut.runtime.http.scope.RequestScope;

@RequestScope
class RequestBean {

}
""")

        then:
        beanDefinition.getAnnotationNameByStereotype(AnnotationUtil.SCOPE).get() == RequestScope.name
    }

    void "test bean definition data"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(Environment.TEST)
        BeanDefinition aDefinition = applicationContext.getBeanDefinition(RequestBean)

        expect:
        aDefinition.getAnnotationNameByStereotype(AnnotationUtil.SCOPE).isPresent()
        aDefinition.getAnnotationNameByStereotype(AnnotationUtil.SCOPE).get() == RequestScope.name
    }

    @RequestScope
    static class RequestBean {

        int num = 0

        int count() {
            num++
            return num
        }
    }
}
