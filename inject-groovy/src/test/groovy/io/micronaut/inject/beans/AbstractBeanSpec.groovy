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
package io.micronaut.inject.beans

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

/**
 * @author graemerocher
 * @since 1.0
 */
class AbstractBeanSpec extends AbstractBeanDefinitionSpec {

    void "test that abstract bean definitions are built for abstract classes"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('abbean1.AbstractBean', '''
package abbean1;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
abstract class AbstractBean {
    @Value("server.host")
    String host

}
''')
        then:
        beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedMethods.size() == 1
    }
}
