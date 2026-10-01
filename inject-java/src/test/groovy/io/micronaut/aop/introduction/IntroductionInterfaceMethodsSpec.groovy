/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.aop.introduction

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class IntroductionInterfaceMethodsSpec extends AbstractTypeElementSpec {

    void "test only abstract methods are proxied when the type declares no around advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;

@Stub
@jakarta.inject.Singleton
interface MyBean {

    String abstractMethod();

    default String defaultMethod() {
        return "default";
    }
}
''')

        then:
        beanDefinition.getExecutableMethods()*.name.toSorted() == ["abstractMethod"]
    }

    void "test a default method declaring around advice is proxied"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;

@Stub
@jakarta.inject.Singleton
interface MyBean {

    String abstractMethod();

    @Tx
    default String advisedDefaultMethod() {
        return "default";
    }

    default String defaultMethod() {
        return "default";
    }
}
''')

        then:
        beanDefinition.getExecutableMethods()*.name.toSorted() == ["abstractMethod", "advisedDefaultMethod"]
    }

    void "test static and private methods are not proxied when the type declares around advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;

@Stub
@Tx
@jakarta.inject.Singleton
interface MyBean {

    String abstractMethod();

    default String defaultMethod() {
        return privateMethod() + staticMethod();
    }

    private String privateMethod() {
        return "private";
    }

    static String staticMethod() {
        return "static";
    }
}
''')

        then:
        beanDefinition.getExecutableMethods()*.name.toSorted() == ["abstractMethod", "defaultMethod"]
    }
}
