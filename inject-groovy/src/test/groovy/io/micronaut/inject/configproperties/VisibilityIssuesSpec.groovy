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
package io.micronaut.inject.configproperties

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory

class VisibilityIssuesSpec extends AbstractBeanDefinitionSpec {

    void "test extending a class with protected method in a different package"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition("io.micronaut.inject.configproperties.ChildConfigProperties", """
            package io.micronaut.inject.configproperties;

            import io.micronaut.context.annotation.ConfigurationProperties;
            import io.micronaut.inject.configproperties.other.ParentConfigProperties;

            @ConfigurationProperties("child")
            class ChildConfigProperties extends ParentConfigProperties {

                Integer age
            }
        """)

        when:
        def context = ApplicationContext.run(
                'parent.name': 'Sally',
                'parent.child.age': 22,
                'parent.engine.manufacturer': 'Chevy')
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)

        then:
            instance.getName() == null //methods that require reflection are not injected
        instance.getAge() == 22
        instance.getBuilder().build().getManufacturer() == 'Chevy'

        cleanup:
        context.close()
    }

    void "test extending a class with protected field in a different package"() {
        when:
        // Micronaut 3: not configured with parent.child.name because non public methods are ignored
        // Micronaut 4: correctly using parent.child.name
        def context = ApplicationContext.run('parent.nationality': 'Italian', 'parent.child.name': 'Sally')
        def instance = context.getBean(ChildConfigProperties)

        then:
        instance.nationality == "Italian" //fields that require reflection are injected
        instance.getName() == 'Sally'

        cleanup:
        context.close()
    }

}
