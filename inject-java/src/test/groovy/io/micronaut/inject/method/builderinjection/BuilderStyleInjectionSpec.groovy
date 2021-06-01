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
package io.micronaut.inject.method.builderinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.BeanFactory
import spock.lang.Issue

class BuilderStyleInjectionSpec extends AbstractTypeElementSpec {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/1070")
    void "test that it is possible to inject a method return returns itself"() {
        when:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test {
    public java.net.URL url;
    
    @jakarta.inject.Inject
    Test setURL( java.net.URL url) {
        this.url = url;
        return this;
    }
}

''')
        then:
        definition != null
        definition.getBeanType().name == 'test.Test'



        when:
        def context = new DefaultBeanContext()
        def url = new URL("http://localhost")
        context.registerSingleton(url)
        def test = ((BeanFactory)definition).build(
                context, definition
        )

        then:
        test.url == url
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/1070")
    void "test configuration properties that return returns itself"() {
        when:
        BeanDefinition definition = buildBeanDefinition('test.TestConfig','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@ConfigurationProperties("foo")
class TestConfig {


    public java.net.URL url;
    private java.net.URL anotherUrl;
    
    private String name;
    
    public void setName(String name) {
        this.name = name;
    }   
    
    public String getName() {
        return this.name;
    }
    
    @jakarta.inject.Inject
    TestConfig setURL( java.net.URL url) {
        this.url = url;
        return this;
    }
    
    @jakarta.inject.Inject
    void setAnotherURL( java.net.URL url) {
        this.anotherUrl = url;
    }
}

''')
        then:
        definition != null
        definition.getBeanType().name == 'test.TestConfig'



        when:
        def context = new DefaultBeanContext()
        def url = new URL("http://localhost")
        context.registerSingleton(url)
        def test = ((BeanFactory)definition).build(
                context, definition
        )

        then:
        test.url == url
    }
}
