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

@ConfigurationProperties("test.props")    
final class TestProps {
    @ConfigurationBuilder(prefixes = "with") 
    private Engine.Builder builder = Engine.builder();

    public final Engine.Builder getBuilder() {
        return builder;
    }
}

class Engine {
    private final String manufacturer; 

    public Engine(String manufacturer) {
        this.manufacturer = manufacturer;
    }
    
    public String getManufacturer() {
        return manufacturer;    
    }
    
    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private String manufacturer = "Ford";

        public Builder withManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
            return this;
        }

        Engine build() {
            return new Engine(manufacturer);
        }
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

@ConfigurationProperties("test.props")    
final class TestProps {
    @ConfigurationBuilder(prefixes = "with") 
    private Engine.Builder builder = Engine.builder();
}

class Engine {
    private final String manufacturer; 

    public Engine(String manufacturer) {
        this.manufacturer = manufacturer;
    }
    
    public String getManufacturer() {
        return manufacturer;    
    }
    
    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private String manufacturer = "Ford";

        public Builder withManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
            return this;
        }

        Engine build() {
            return new Engine(manufacturer);
        }
    }
}
''')

        then:
        RuntimeException ex = thrown()
        ex.message.contains("ConfigurationBuilder applied to a private field must have a corresponding non-private getter method.")
        ex.message.contains("private Engine.Builder builder = Engine.builder();")

    }
}
