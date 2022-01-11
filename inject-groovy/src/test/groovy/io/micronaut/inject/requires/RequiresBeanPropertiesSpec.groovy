package io.micronaut.inject.requires

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NoSuchBeanException

class RequiresBeanPropertiesSpec extends AbstractBeanDefinitionSpec {

    void "test requires bean property presence"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("config")
class Config {

    private String property

    String getProperty() {
        return property
    }

    void setProperty(String property) {
        this.property = property
    }
}

@Singleton
@Requires(bean = Config.class, beanProperty = "property")
class PresentPropertyDependantBean {}
''')
        def type = context.classLoader.loadClass('test.PresentPropertyDependantBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test", ['config.property': 'anyValue']))
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test bean property absence"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("config")
class Config {

    private String property

    String getProperty() {
        return property
    }

    void setProperty(String property) {
        this.property = property
    }
}

@Singleton
@Requires(bean = Config.class, beanProperty = "property")
class AbsentPropertyDependantBean {}
''')
        def type = context.classLoader.loadClass('test.AbsentPropertyDependantBean')

        when:
        context.getBean(type)

        then:
        thrown(NoSuchBeanException.class)

        cleanup:
        context.close()
    }

    void "test requires inner configuration property presence"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("outer")
class OuterConfig {

    private String outerProperty

    String getOuterProperty() {
        return outerProperty
    }

    void setOuterProperty(String outerProperty) {
        this.outerProperty = outerProperty
    }

    @Introspected
    @ConfigurationProperties("inner")
    static class InnerConfig {

        private String innerProperty

        String getInnerProperty() {
            return innerProperty
        }

        void setInnerProperty(String innerProperty) {
            this.innerProperty = innerProperty
        }
    }
}

@Singleton
@Requires(bean = OuterConfig.class, beanProperty = "outerProperty", value = "outer-enabled")
@Requires(bean = OuterConfig.InnerConfig.class, beanProperty = "innerProperty", value = "inner-enabled")
class InnerPropertyDependentConfig {}
''')
        def type = context.classLoader.loadClass('test.InnerPropertyDependentConfig')

        when:
        context.environment.addPropertySource(PropertySource.of("test",
                ['outer.outer-property'      : 'outer-enabled',
                 'outer.inner.inner-property': 'inner-enabled']))
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test requires multiple beans properties"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("type")
class TypesConfig {

    private String stringProperty
    private Boolean boolProperty
    private Integer intProperty

    Boolean isBoolProperty() {
        return boolProperty
    }

    void setBoolProperty(boolean boolProperty) {
        this.boolProperty = boolProperty
    }

    Integer getIntProperty() {
        return intProperty
    }

    void setIntProperty(int intProperty) {
        this.intProperty = intProperty
    }

    String getStringProperty() {
        return stringProperty
    }

    void setStringProperty(String stringProperty) {
        this.stringProperty = stringProperty
    }
}

@ConfigurationProperties("inherited")
class InheritedConfig extends SimpleConfig {
    private String inheritedProperty

    String getInheritedProperty()
    {
        return inheritedProperty
    }

    void setInheritedProperty(String inheritedProperty)
    {
        this.inheritedProperty = inheritedProperty
    }
}

@ConfigurationProperties("outer")
class SimpleConfig {

    private String simpleConfigProperty

    String getSimpleConfigProperty() {
        return simpleConfigProperty
    }

    void setSimpleConfigProperty(String simpleConfigProperty) {
        this.simpleConfigProperty = simpleConfigProperty
    }
}

@Singleton
@Requires(bean = InheritedConfig.class, beanProperty = "inheritedProperty", value = "inheritedPropertyValue")
@Requires(bean = TypesConfig.class, beanProperty = "intProperty", value = "1")
@Requires(bean = SimpleConfig.class, beanProperty = "simpleConfigProperty", notEquals = "disabled")
@Requires(bean = TypesConfig.class, beanProperty = "stringProperty", value = "test")
class DifferentTypesProperties {}
''')
        def type = context.classLoader.loadClass('test.DifferentTypesProperties')

        when:
        context.environment.addPropertySource(PropertySource.of("test",
                ['type.int-property'                 : '1',
                 'type.string-property'              : 'test',
                 'outer.outer-property'              : 'enabled',
                 'outer.inherited.inherited-property': 'inheritedPropertyValue']))
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test accessor style property"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("accessor")
@AccessorsStyle(readPrefixes = ["read"], writePrefixes = ["write"])
class AccessorStyleConfig {

    private String accessorStyleProperty

    String readAccessorStyleProperty() {
        return accessorStyleProperty
    }

    void writeAccessorStyleProperty(String accessorStyleProperty)
    {
        this.accessorStyleProperty = accessorStyleProperty
    }
};

@Singleton
@Requires(bean = AccessorStyleConfig.class, beanProperty = "accessorStyleProperty")
class AccessorStyleBean {
}
''')
        def type = context.classLoader.loadClass('test.AccessorStyleBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test", ['accessor.accessor-style-property': 'value']))
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test primitive properties"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("test")
class PrimitiveConfig
{
    int intProperty
    int anotherIntProperty
    boolean boolProperty

    int getIntProperty() {
        return intProperty
    }
    
    void setIntProperty(int intProperty) {
        this.intProperty = intProperty
    }

    int getAnotherIntProperty() {
        return anotherIntProperty
    }
    
    void setAnotherIntProperty(int anotherIntProperty) {
        this.anotherIntProperty = anotherIntProperty
    }
    
    boolean isBoolProperty() {
        return boolProperty
    }

    void setBoolProperty(boolean boolProperty) {
        this.boolProperty = boolProperty
    }
}

@Singleton
@Requires(bean = PrimitiveConfig.class, beanProperty = "intProperty", value = "1")
@Requires(bean = PrimitiveConfig.class, beanProperty = "anotherIntProperty", value = "0")
@Requires(bean = PrimitiveConfig.class, beanProperty = "boolProperty", value = "false")
class PrimitivesDependantBean
{
}
''')
        def type = context.classLoader.loadClass('test.PrimitivesDependantBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test", ['test.int-property': 1]))
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test not configuration properties"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@Singleton
class NotConfigurationProperties
{
    int intProperty
    boolean boolProperty
    @Property(name = "test.string-property", defaultValue = "disabled")
    String stringProperty

    int getIntProperty() {
        return intProperty
    }
    
    void setIntProperty(int intProperty) {
        this.intProperty = intProperty
    }
    
    boolean isBoolProperty() {
        return boolProperty
    }

    void setBoolProperty(boolean boolProperty) {
        this.boolProperty = boolProperty
    }
    
    String getStringProperty() {
        return stringProperty
    }
    
    void setStringProperty(String stringProperty) {
        this.stringProperty = stringProperty
    }
}

@Singleton
@Requires(bean = NotConfigurationProperties.class, beanProperty = "intProperty", value = "0")
@Requires(bean = NotConfigurationProperties.class, beanProperty = "boolProperty", value = "false")
@Requires(bean = NotConfigurationProperties.class, beanProperty = "stringProperty")
class NotConfigPropertiesDependantBean {
}
''')
        def type = context.classLoader.loadClass('test.NotConfigPropertiesDependantBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test", [
                'test.string-property': 'enabled'
        ]))
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test bean factory"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.ConfigurationProperties;import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property;import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@ConfigurationProperties("test")
class RequiredBean
{
    private Integer intProperty
    private String stringProperty

    Integer getIntProperty() {
        return intProperty
    }

    void setIntProperty(Integer intProperty) {
        this.intProperty = intProperty
    }

    String getStringProperty() {
        return stringProperty
    }

    void setStringProperty(String stringProperty) {
        this.stringProperty = stringProperty
    }
}

class TestBean{
}

@Factory
class TestBeanFactory
{
    @Bean
    @Requires(bean = RequiredBean.class, beanProperty = "intProperty", value = "1")
    @Requires(bean = RequiredBean.class, beanProperty = "stringProperty", value = "enabled")
    TestBean testBean() {
        return new TestBean()
    }
}
''')
        def type = context.classLoader.loadClass('test.TestBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test", [
                'test.string-property': 'enabled',
                'test.int-property'   : '1'
        ]))
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }
}
