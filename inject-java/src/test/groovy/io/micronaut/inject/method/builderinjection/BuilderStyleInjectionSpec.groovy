package io.micronaut.inject.method.builderinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.AbstractTypeElementSpec
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

@javax.inject.Singleton
class Test {
    public java.net.URL url;
    
    @javax.inject.Inject
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
    
    @javax.inject.Inject
    TestConfig setURL( java.net.URL url) {
        this.url = url;
        return this;
    }
    
    @javax.inject.Inject
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
