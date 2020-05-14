package io.micronaut.inject.configuration

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.AbstractTypeElementSpec

class ConfigurationBuilderSpec extends AbstractTypeElementSpec {

    void "test definition uses getter instead of field"() {
        given:
        ApplicationContext ctx = buildContext("test.TestProps", '''
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
        ApplicationContext ctx = buildContext("test.TestProps", '''
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
        RuntimeException ex = thrown()
        ex.message.contains("ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.")
        ex.message.contains("private Engine.Builder builder = Engine.builder();")

    }

    void "test config field with setter abnormal paramater name"() {
        given:
        ApplicationContext ctx = buildContext("test.TestProps", '''
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

    void "test configuration builder that are interfaces"() {
        given:
        ApplicationContext ctx = buildContext("test.PoolConfig", '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("pool")    
final class PoolConfig { 
    
    @ConfigurationBuilder(prefixes = {""})
    public ConnectionPool.Builder builder = DefaultConnectionPool.builder();
    
}

interface ConnectionPool {
    
    interface Builder {
        Builder maxConcurrency(Integer maxConcurrency);
        ConnectionPool build();
    }
    
    int getMaxConcurrency();
}

class DefaultConnectionPool implements ConnectionPool {
    private final int maxConcurrency;
    
    DefaultConnectionPool(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }
    
    public static ConnectionPool.Builder builder() {
        return new DefaultBuilder();
    }
    
    @Override 
    public int getMaxConcurrency() {
        return maxConcurrency;
    }
    
    private static class DefaultBuilder implements ConnectionPool.Builder {
    
        private int maxConcurrency;
    
        private DefaultBuilder() {
        }
    
        @Override
        public ConnectionPool.Builder maxConcurrency(Integer maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }
        
        public ConnectionPool build() {
            return new DefaultConnectionPool(maxConcurrency);
        }
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of(["pool.max-concurrency": 123]))

        when:
        Class testProps = ctx.classLoader.loadClass("test.PoolConfig")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()
        testPropBean.builder.build().getMaxConcurrency() == 123
    }

}
