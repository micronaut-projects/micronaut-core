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
package io.micronaut.inject.errors

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

class GroovySingletonSpec extends AbstractBeanDefinitionSpec {

    void "test that compilation fails if injection visited on Groovy singleton"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.inject.property.MyBean', '''
package io.micronaut.inject.property;

import io.micronaut.inject.qualifiers.*

@Singleton
class MyBean  {
    @jakarta.inject.Inject
    @javax.annotation.Nullable
    AnotherBean injected
}

@jakarta.inject.Singleton
class AnotherBean {
    
}
''')
        then:
        def e = thrown(Exception)
        e.message.contains("Class annotated with groovy.lang.Singleton instead of jakarta.inject.Singleton. Import jakarta.inject.Singleton to use Micronaut Dependency Injection")

    }

}
