package io.micronaut.inject.configuration

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import org.codehaus.groovy.control.MultipleCompilationErrorsException

class GroovyConfigBuilderSpec extends AbstractBeanDefinitionSpec {

    void "test definition uses getter instead of field"() {
        given:
        ApplicationContext ctx = buildContext('''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.Engine;

@ConfigurationProperties("test.props")    
final class TestProps {
    @ConfigurationBuilder(prefixes = "with") 
    private Engine.Builder builder = Engine.builder();

    public final Engine.Builder getBuilder() {
        return builder;
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of(["test.props.manufacturer": "Toyota"]))

        when:
        Class testProps = ctx.classLoader.loadClass("test.TestProps")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()
        ctx.getProperty("test.props.manufacturer", String).get() == "Toyota"
        testPropBean.getBuilder().build().getManufacturer() == "Toyota"
    }

    void "test private config field with no getter throws an error"() {
        when:
        ApplicationContext ctx = buildContext('''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.Engine;

@ConfigurationProperties("test.props")    
final class TestProps {
    @ConfigurationBuilder(prefixes = "with") 
    private Engine.Builder builder = Engine.builder();
}
''')

        then:
        MultipleCompilationErrorsException ex = thrown()
        ex.message.contains("ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.")
        ex.message.contains("@ConfigurationBuilder(prefixes = \"with\")")
    }

    void "test config field with setter abnormal paramater name"() {
        given:
        ApplicationContext ctx = buildContext('''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.Engine;

@ConfigurationProperties("test.props")    
final class TestProps {     
    Engine.Builder builder = Engine.builder();
    
    Engine.Builder getBuilder() {
        return this.builder;
    }
    
    @ConfigurationBuilder(prefixes = "with")
    void setBuilder(Engine.Builder p0) {
        this.builder = p0;
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of(["test.props.manufacturer": "Toyota"]))

        when:
        Class testProps = ctx.classLoader.loadClass("test.TestProps")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()
        ctx.getProperty("test.props.manufacturer", String).get() == "Toyota"
        testPropBean.getBuilder().build().getManufacturer() == "Toyota"

    }
}
