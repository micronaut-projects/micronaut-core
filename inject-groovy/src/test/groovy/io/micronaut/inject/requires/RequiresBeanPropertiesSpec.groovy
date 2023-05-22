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
    String property
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

    void "test requires bean property with absent property"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("config")
class Config {
    String property
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


    void "test requires not equals property value with value not set"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("test")
class Config
{
    Boolean boolProperty
}

@Singleton
@Requires(bean = Config.class, beanProperty = "boolProperty", notEquals = "true")
class DependantBean
{
}
''')
        def type = context.classLoader.loadClass('test.DependantBean')

        when:
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test requires not equals property value with value set"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("test")
class Config
{
    Boolean boolProperty
}

@Singleton
@Requires(bean = Config.class, beanProperty = "boolProperty", notEquals = "true")
class DependantBean
{
}
''')
        def type = context.classLoader.loadClass('test.DependantBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test", ['test.bool-property': "true"]))
        context.getBean(type)

        then:
        thrown(NoSuchBeanException.class)

        cleanup:
        context.close()
    }

    void "test requires not equals property value with primitive value set"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("test")
class Config
{
    boolean boolProperty
}

@Singleton
@Requires(bean = Config.class, beanProperty = "boolProperty", notEquals = "true")
class DependantBean
{
}
''')
        def type = context.classLoader.loadClass('test.DependantBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test", ['test.bool-property': "true"]))
        context.getBean(type)

        then:
        thrown(NoSuchBeanException.class)

        cleanup:
        context.close()
    }

    void "test requires bean property value with getters and setters"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("config.properties")
class Config {
    private String property

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }
}

@Singleton
@Requires(bean = Config.class, beanProperty = "property", value = "required value")
class AbsentPropertyDependantBean {}
''')
        def type = context.classLoader.loadClass('test.AbsentPropertyDependantBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test",
                ['config.properties.property': 'required value']))
        context.getBean(type)

        then:
        noExceptionThrown()

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

    String outerProperty

    @ConfigurationProperties("inner")
    static class InnerConfig {
        String innerProperty
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

    void "test requires inner configuration property with absent property"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("outer")
class OuterConfig {

    String outerProperty

    @ConfigurationProperties("inner")
    static class InnerConfig {
        String innerProperty
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
                ['outer.outer-property': 'outer-enabled']))
        context.getBean(type)

        then:
        thrown(NoSuchBeanException.class)

        cleanup:
        context.close()
    }

    void "test requires inherited config property with absent property"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("outer")
class ParentConfig {
    String simpleConfigProperty
}

@ConfigurationProperties("inherited")
class InheritedConfig extends ParentConfig {
    String inheritedProperty
}

@Singleton
@Requires(bean = InheritedConfig.class, beanProperty = "inheritedProperty")
class DifferentTypesProperties {}
''')
        def type = context.classLoader.loadClass('test.DifferentTypesProperties')

        when:
        context.getBean(type)

        then:
        thrown(NoSuchBeanException.class)

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
    String stringProperty
    Boolean boolProperty
    Integer intProperty
}

@ConfigurationProperties("inherited")
class InheritedConfig extends SimpleConfig {
    String inheritedProperty
}

@ConfigurationProperties("outer")
class SimpleConfig {
    String simpleConfigProperty
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

    void "test requires multiple beans properties with absent property"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("type")
class TypesConfig {
    String stringProperty
    Boolean boolProperty
    Integer intProperty
}

@ConfigurationProperties("inherited")
class InheritedConfig extends SimpleConfig {
    String inheritedProperty
}

@ConfigurationProperties("outer")
class SimpleConfig {
    String simpleConfigProperty
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
                ['type.string-property'              : 'test',
                 'outer.outer-property'              : 'enabled',
                 'outer.inherited.inherited-property': 'inheritedPropertyValue']))
        context.getBean(type)

        then:
        thrown(NoSuchBeanException.class)

        cleanup:
        context.close()
    }

    void "test requires multiple beans properties with notEquals value set"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.inject.Singleton

@ConfigurationProperties("type")
class TypesConfig {
    String stringProperty
    Boolean boolProperty
    Integer intProperty
}

@ConfigurationProperties("inherited")
class InheritedConfig extends SimpleConfig {
    String inheritedProperty
}

@ConfigurationProperties("outer")
class SimpleConfig {
    String simpleConfigProperty
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
                ['type.string-property'              : 'test',
                 'type.int-property'                 : '1',
                 'outer.outer-property'              : 'enabled',
                 'outer.simple-config-property'      : 'disabled',
                 'outer.inherited.inherited-property': 'inheritedPropertyValue']))
        context.getBean(type)

        then:
        thrown(NoSuchBeanException.class)

        cleanup:
        context.close()
    }

    void "test requires accessor style property"() {
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

    void "test requires primitive properties with default values"() {
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

    void "test requires not configuration properties"() {
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

    void "test requires in bean factory"() {
        given:
        ApplicationContext context = buildContext('''
package test
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.ConfigurationProperties;import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property;import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@ConfigurationProperties("test")
class Config
{
    Integer intProperty
    String stringProperty

    @ConfigurationProperties("inner")
    static class InnerConfig {
        String innerProperty = "default value"
    }
}

class TestBean{
}

@Factory
class TestBeanFactory
{
    @Bean
    @Requires(bean = Config.class, beanProperty = "intProperty", value = "1")
    @Requires(bean = Config.class, beanProperty = "stringProperty", value = "enabled")
    @Requires(bean = Config.InnerConfig.class, beanProperty = "innerProperty", value = "default value")
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

    void "test requires with default interface methods"() {
        given:
        ApplicationContext context = buildContext('''
package test;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.Toggleable;
import jakarta.inject.Singleton;

interface Configuration extends Toggleable {}

@Singleton
class ConfigurationImpl implements Configuration
{
    boolean enabled = false;

    @Override
    boolean isEnabled() {
        return enabled;
    }
}

@Requires(bean = Configuration.class, beanProperty = "enabled", value = "true")
@Singleton
class TestBean {
}
''')
        def type = context.classLoader.loadClass('test.TestBean')

        when:
        context.getBean(type)

        then:
        thrown(NoSuchBeanException.class)

        cleanup:
        context.close()

    }
}
