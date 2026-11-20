/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.inject.ast.ClassElement
import spock.lang.Unroll

class BeanDefinitionCreatorFactoryMayProduceSpec extends AbstractTypeElementSpec {

    @Unroll
    void "mayProduce is #expected for #description and agrees with the definitions the compiler writes"() {
        given:
        String source = "package test;\n" + code

        when:
        boolean mayProduce = buildClassElement(source) { ClassElement element ->
            BeanDefinitionCreatorFactory.mayProduce(element)
        }
        ClassLoader classLoader = buildClassLoader("test.Test", source)

        then:
        mayProduce == expected
        hasDefinition(classLoader) == expected

        where:
        description                                  | expected | code
        'a plain class'                              | false    | 'class Test { String name; void run() {} }'
        'a class with a scope'                       | true     | '@jakarta.inject.Singleton class Test {}'
        'a class declared a bean'                    | true     | '@io.micronaut.context.annotation.Bean class Test {}'
        'an executable class'                        | true     | '@io.micronaut.context.annotation.Executable class Test { public void run() {} }'
        'a qualified class'                          | true     | '@jakarta.inject.Named("test") class Test {}'
        'a class with an injected constructor'       | true     | 'class Test { @jakarta.inject.Inject Test(Runnable r) {} }'
        'a class with an injected method'            | true     | 'class Test { @jakarta.inject.Inject void set(Runnable r) {} }'
        'a class with an injected field'             | true     | 'class Test { @jakarta.inject.Inject Runnable r; }'
        'a class with a value field'                 | true     | 'class Test { @io.micronaut.context.annotation.Value("x") String x; }'
        'a class with a property field'              | true     | 'class Test { @io.micronaut.context.annotation.Property(name = "x") String x; }'
        'an abstract class with an injected field'   | false    | 'abstract class Test { @jakarta.inject.Inject Runnable r; }'
        'an abstract executable class'               | false    | '@io.micronaut.context.annotation.Executable abstract class Test { public void run() {} }'
        'an interface'                               | false    | 'interface Test { void run(); }'
        'an interface with a scope'                  | false    | '@jakarta.inject.Singleton interface Test { void run(); }'
        'a configuration class'                      | true     | '@io.micronaut.context.annotation.ConfigurationProperties("test") class Test { private String name; public String getName() { return name; } public void setName(String name) { this.name = name; } }'
        'a configuration interface'                  | true     | '@io.micronaut.context.annotation.ConfigurationProperties("test") interface Test { String getName(); }'
        'a factory'                                  | true     | '@io.micronaut.context.annotation.Factory class Test {}'
        'an abstract factory, a bean by its scope'    | true     | '@io.micronaut.context.annotation.Factory abstract class Test {}'
        'an advised class'                           | true     | '@io.micronaut.aop.Logged class Test { public void run() {} }'
        'an introduced interface'                    | true     | '@io.micronaut.aop.introduction.Stub interface Test { String run(); }'
        'an introduced abstract class'               | true     | '@io.micronaut.aop.introduction.Stub @jakarta.inject.Singleton abstract class Test { public abstract String run(); }'
        'an advised and introduced class'            | true     | ADVISED_AND_INTRODUCED
    }

    void "a factory gets the definitions of the beans it produces"() {
        given:
        String source = 'package test;\n@io.micronaut.context.annotation.Factory class Test { @io.micronaut.context.annotation.Bean Product product() { return new Product(); } }\nclass Product {}'

        expect:
        buildClassElement(source) { ClassElement element -> BeanDefinitionCreatorFactory.mayProduce(element) }

        when:
        ApplicationContext context = buildContext('test.Test', source)

        then:
        context.findBeanDefinition(context.classLoader.loadClass('test.Product')).isPresent()

        cleanup:
        context?.close()
    }

    void "an advised and introduced class gets a proxy with both advices"() {
        when:
        ClassLoader classLoader = buildClassLoader('test.Test', 'package test;\n' + ADVISED_AND_INTRODUCED)
        Class<?> proxy = classLoader.loadClass('test.Test$Intercepted')

        then:
        classLoader.loadClass('test.Test').isAssignableFrom(proxy)
        Intercepted.isAssignableFrom(proxy)
        ApplicationEventListener.isAssignableFrom(proxy)
    }

    void "mayProduce leaves out the veto of the language processor: #description"() {
        given:
        String source = 'package test;\n@jakarta.inject.Singleton class Test {}'
        String vetoedSource = 'package test;\n@io.micronaut.core.annotation.Vetoed @jakarta.inject.Singleton class Test {}'
        String packageInfo = '@io.micronaut.core.annotation.Vetoed package test;'

        when:
        boolean mayProduce = vetoedPackage ?
            buildClassElement(packageInfo, source) { ClassElement element -> BeanDefinitionCreatorFactory.mayProduce(element) } :
            buildClassElement(vetoedSource) { ClassElement element -> BeanDefinitionCreatorFactory.mayProduce(element) }
        ApplicationContext context = vetoedPackage ?
            buildContext(packageInfo, 'test.Test', source) :
            buildContext('test.Test', vetoedSource)

        then: 'the creator is chosen, but the Java processor skips the class before produce is called'
        mayProduce
        !context.allBeanDefinitions.any { it.beanType.name == 'test.Test' }

        cleanup:
        context?.close()

        where:
        description           | vetoedPackage
        'a vetoed class'      | false
        'a vetoed package'    | true
    }

    private static final String ADVISED_AND_INTRODUCED = '@io.micronaut.aop.introduction.ListenerAdvice @io.micronaut.aop.Logged @jakarta.inject.Singleton class Test { public void run() {} }'

    private static boolean hasDefinition(ClassLoader classLoader) {
        return ['test.$Test$Definition', 'test.$Test$Intercepted$Definition'].any { String name ->
            try {
                classLoader.loadClass(name)
                return true
            } catch (ClassNotFoundException ignored) {
                return false
            }
        }
    }
}
