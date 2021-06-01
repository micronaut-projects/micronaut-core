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
package io.micronaut.runtime.context.scope

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Scope

class ThreadLocalJavaParseSpec extends io.micronaut.annotation.processing.test.AbstractTypeElementSpec {

    void "test parse bean definition data"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.ThreadLocalBean', '''
package test;

import io.micronaut.runtime.context.scope.*;

@io.micronaut.runtime.context.scope.ThreadLocal()
class ThreadLocalBean {

}
''')

        then:
        beanDefinition.getAnnotationNameByStereotype(AnnotationUtil.SCOPE).get() == ThreadLocal.name

    }
}
