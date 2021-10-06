package io.micronaut.validation.requires

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class RequiresConfigPropsSpec extends AbstractTypeElementSpec {


    void "test requires config property presence"() {

        when:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;import jakarta.inject.Singleton;

@ConfigurationProperties("test")
class TestConfig {
    
    private String testProperty;
    
    public String getTestProperty() {
        return testProperty;
    }
    
    public void setTestProperty(String testProperty) {
        this.testProperty = testProperty;
    }
}

@Singleton
@Requires(configProperties = TestConfig.class, configProperty = "testProperty")
class RequiresProperty {}
''')

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test required config property absence fails compilation"() {

        when:
        buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;import jakarta.inject.Singleton;

@ConfigurationProperties("test")
class TestConfig {
    
    private String testProperty;
    
    public String getTestProperty() {
        return testProperty;
    }
    
    public void setTestProperty(String testProperty) {
        this.testProperty = testProperty;
    }
}

@Singleton
@Requires(configProperties = TestConfig.class, configProperty = "absentProperty")
class RequiresProperty {}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Configuration property [absentProperty] is not present on [test.TestConfig] class")
    }

    void "test required config property presence on nested class"() {

        when:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;import jakarta.inject.Singleton;

@ConfigurationProperties("outer")
class OuterConfig {
    
    private String testProperty;
    
    public String getTestProperty() {
        return testProperty;
    }
    
    public void setTestProperty(String testProperty) {
        this.testProperty = testProperty;
    }
    
    @ConfigurationProperties("inner")
    public static class InnerConfig {
        
        private String innerProperty;
        
        public String getInnerProperty() {
            return innerProperty;
        }
        
        public void setInnerProperty(String innerProperty) {
            this.innerProperty = innerProperty;
        }
    }
}

@Singleton
@Requires(configProperties = OuterConfig.InnerConfig.class, configProperty = "innerProperty")
class RequiresProperty {}
''')

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test required config property presence on inherited class"() {

        when:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;import jakarta.inject.Singleton;

@ConfigurationProperties("parent")
class ParentConfig {}

@ConfigurationProperties("child")
class ChildConfig {
    private String childProperty;
    
    public String getChildProperty() {
        return this.childProperty;
    }
    
    public void setChildProperty(String childProperty) {
        this.childProperty = childProperty;
    }
}

@Singleton
@Requires(configProperties = ChildConfig.class, configProperty = "childProperty")
class RequiresProperty {}
''')

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test required config property presence on interface"() {

        when:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;import jakarta.inject.Singleton;

@ConfigurationProperties("interface")
interface InterfaceConfig {
    String getProperty();
}

@Singleton
@Requires(configProperties = InterfaceConfig.class, configProperty = "property")
class RequiresProperty {}
''')

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test specifying not configuration properties class"() {

        when:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;import jakarta.inject.Singleton;

class Config {

    private String property;

    public String getProperty() {
        return property;
    }
    
    public void setProperty(String property) {
        this.property = property;
    }
}

@Singleton
@Requires(configProperties = Config.class, configProperty = "property")
class RequiresProperty {}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Specified class [test.Config] must be configuration properties bean")
    }


}
