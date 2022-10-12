package io.micronaut.inject.requires

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NoSuchBeanException
import spock.lang.IgnoreIf

class RequiresBeanPropertiesSpec extends AbstractTypeElementSpec {

    void "test requires bean property presence"() {
        given:
        ApplicationContext context = buildContext('test.PresentPropertyDependantBean', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("config")
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
@Requirements({
    @Requires(bean = Config.class, beanProperty = "property"),
})
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
        ApplicationContext context = buildContext('test.AbsentPropertyDependantBean', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("config")
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
@Requirements({
    @Requires(bean = Config.class, beanProperty = "property"),
})
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
        ApplicationContext context = buildContext('test.PresentPropertyDependantBean', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("config")
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
@Requires(bean = Config.class, beanProperty = "property", notEquals = "concreteValue")
class PresentPropertyDependantBean {}
''')
        def type = context.classLoader.loadClass('test.PresentPropertyDependantBean')

        when:
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test requires not equals property value with value set"() {
        given:
        ApplicationContext context = buildContext('test.PresentPropertyDependantBean', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("config")
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
@Requires(bean = Config.class, beanProperty = "property", notEquals = "concreteValue")
class PresentPropertyDependantBean {}
''')
        def type = context.classLoader.loadClass('test.PresentPropertyDependantBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test", ['config.property': 'concreteValue']))
        context.getBean(type)

        then:
        thrown(NoSuchBeanException.class)

        cleanup:
        context.close()
    }

    void "test requires inner configuration property presence"() {
        given:
        ApplicationContext context = buildContext('test.InnerPropertyDependentConfig', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("outer")
class OuterConfig {

    private String outerProperty;

    public String getOuterProperty() {
        return outerProperty;
    }

    public void setOuterProperty(String outerProperty) {
        this.outerProperty = outerProperty;
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
        ApplicationContext context = buildContext('test.InnerPropertyDependentConfig', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("outer")
class OuterConfig {

    private String outerProperty;

    public String getOuterProperty() {
        return outerProperty;
    }

    public void setOuterProperty(String outerProperty) {
        this.outerProperty = outerProperty;
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
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("outer")
class ParentConfig {
    private String simpleConfigProperty;

    public String getSimpleConfigProperty() {
        return simpleConfigProperty;
    }

    public void setSimpleConfigProperty(String simpleConfigProperty) {
        this.simpleConfigProperty = simpleConfigProperty;
    }
}

@ConfigurationProperties("inherited")
class InheritedConfig extends ParentConfig {
    private String inheritedProperty;

    public String getInheritedProperty() {
        return inheritedProperty;
    }

    public void setInheritedProperty(String inheritedProperty) {
        this.inheritedProperty = inheritedProperty;
    }
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
        ApplicationContext context = buildContext('test.DifferentTypesProperties', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("type")
class TypesConfig {

    private String stringProperty;
    private Boolean boolProperty;
    private Integer intProperty;

    public Boolean isBoolProperty() {
        return boolProperty;
    }

    public void setBoolProperty(boolean boolProperty) {
        this.boolProperty = boolProperty;
    }

    public Integer getIntProperty() {
        return intProperty;
    }

    public void setIntProperty(int intProperty) {
        this.intProperty = intProperty;
    }

    public String getStringProperty() {
        return stringProperty;
    }

    public void setStringProperty(String stringProperty) {
        this.stringProperty = stringProperty;
    }
}

@ConfigurationProperties("inherited")
class InheritedConfig extends SimpleConfig {
    private String inheritedProperty;

    public String getInheritedProperty()
    {
        return inheritedProperty;
    }

    public void setInheritedProperty(String inheritedProperty)
    {
        this.inheritedProperty = inheritedProperty;
    }
}

@ConfigurationProperties("outer")
class SimpleConfig {

    private String simpleConfigProperty;

    public String getSimpleConfigProperty() {
        return simpleConfigProperty;
    }

    public void setSimpleConfigProperty(String simpleConfigProperty) {
        this.simpleConfigProperty = simpleConfigProperty;
    }
}

@Singleton
@Requirements({
    @Requires(bean = InheritedConfig.class, beanProperty = "inheritedProperty", value = "inheritedPropertyValue"),
    @Requires(bean = TypesConfig.class, beanProperty = "intProperty", value = "1"),
    @Requires(bean = SimpleConfig.class, beanProperty = "simpleConfigProperty", notEquals = "disabled"),
    @Requires(bean = TypesConfig.class, beanProperty = "stringProperty", value = "test"),
})
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
        ApplicationContext context = buildContext('test.DifferentTypesProperties', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("type")
class TypesConfig {

    private String stringProperty;
    private Boolean boolProperty;
    private Integer intProperty;

    public Boolean isBoolProperty() {
        return boolProperty;
    }

    public void setBoolProperty(boolean boolProperty) {
        this.boolProperty = boolProperty;
    }

    public Integer getIntProperty() {
        return intProperty;
    }

    public void setIntProperty(int intProperty) {
        this.intProperty = intProperty;
    }

    public String getStringProperty() {
        return stringProperty;
    }

    public void setStringProperty(String stringProperty) {
        this.stringProperty = stringProperty;
    }
}

@ConfigurationProperties("inherited")
class InheritedConfig extends SimpleConfig {
    private String inheritedProperty;

    public String getInheritedProperty()
    {
        return inheritedProperty;
    }

    public void setInheritedProperty(String inheritedProperty)
    {
        this.inheritedProperty = inheritedProperty;
    }
}

@ConfigurationProperties("outer")
class SimpleConfig {

    private String simpleConfigProperty;

    public String getSimpleConfigProperty() {
        return simpleConfigProperty;
    }

    public void setSimpleConfigProperty(String simpleConfigProperty) {
        this.simpleConfigProperty = simpleConfigProperty;
    }
}

@Singleton
@Requirements({
    @Requires(bean = InheritedConfig.class, beanProperty = "inheritedProperty", value = "inheritedPropertyValue"),
    @Requires(bean = TypesConfig.class, beanProperty = "intProperty", value = "1"),
    @Requires(bean = SimpleConfig.class, beanProperty = "simpleConfigProperty", notEquals = "disabled"),
    @Requires(bean = TypesConfig.class, beanProperty = "stringProperty", value = "test"),
})
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
        ApplicationContext context = buildContext('test.DifferentTypesProperties', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("type")
class TypesConfig {

    private String stringProperty;
    private Boolean boolProperty;
    private Integer intProperty;

    public Boolean isBoolProperty() {
        return boolProperty;
    }

    public void setBoolProperty(boolean boolProperty) {
        this.boolProperty = boolProperty;
    }

    public Integer getIntProperty() {
        return intProperty;
    }

    public void setIntProperty(int intProperty) {
        this.intProperty = intProperty;
    }

    public String getStringProperty() {
        return stringProperty;
    }

    public void setStringProperty(String stringProperty) {
        this.stringProperty = stringProperty;
    }
}

@ConfigurationProperties("inherited")
class InheritedConfig extends SimpleConfig {
    private String inheritedProperty;

    public String getInheritedProperty()
    {
        return inheritedProperty;
    }

    public void setInheritedProperty(String inheritedProperty)
    {
        this.inheritedProperty = inheritedProperty;
    }
}

@ConfigurationProperties("outer")
class SimpleConfig {

    private String simpleConfigProperty;

    public String getSimpleConfigProperty() {
        return simpleConfigProperty;
    }

    public void setSimpleConfigProperty(String simpleConfigProperty) {
        this.simpleConfigProperty = simpleConfigProperty;
    }
}

@Singleton
@Requirements({
    @Requires(bean = InheritedConfig.class, beanProperty = "inheritedProperty", value = "inheritedPropertyValue"),
    @Requires(bean = TypesConfig.class, beanProperty = "intProperty", value = "1"),
    @Requires(bean = SimpleConfig.class, beanProperty = "simpleConfigProperty", notEquals = "disabled"),
    @Requires(bean = TypesConfig.class, beanProperty = "stringProperty", value = "test"),
})
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
        ApplicationContext context = buildContext('test.AccessorStyleBean', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("accessor")
@AccessorsStyle(readPrefixes = {"read", "check"})
class AccessorStyleFirstConfig {

    private String firstAccessorStyleProperty;
    private String secondAccessorStyleProperty;

    public String readFirstAccessorStyleProperty() {
        return firstAccessorStyleProperty;
    }

    public void setFirstAccessorStyleProperty(String firstAccessorStyleProperty)
    {
        this.firstAccessorStyleProperty = firstAccessorStyleProperty;
    }

    public String checkSecondAccessorStyleProperty() {
        return secondAccessorStyleProperty;
    }

    public void setSecondAccessorStyleProperty(String secondAccessorStyleProperty) {
        this.secondAccessorStyleProperty = secondAccessorStyleProperty;
    }
}

@Singleton
@Requires(bean = AccessorStyleFirstConfig.class, beanProperty = "firstAccessorStyleProperty", value = "first")
@Requires(bean = AccessorStyleFirstConfig.class, beanProperty = "secondAccessorStyleProperty", value = "second")
class AccessorStyleBean {
}
''')
        def type = context.classLoader.loadClass('test.AccessorStyleBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test",
                ['accessor.first-accessor-style-property' : 'first',
                 'accessor.second-accessor-style-property': 'second']))
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    @IgnoreIf({ !jvm.isJava14Compatible() })
    void "test requires record properties"() {
        given:
        ApplicationContext context = buildContext('test.RecordDependantBean', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.convert.ConversionService;
import javax.validation.constraints.Min;
import jakarta.inject.Inject;
import io.micronaut.context.BeanContext;
import jakarta.inject.Singleton;

@ConfigurationProperties("record")
record Test(int num, String name) {}

@Singleton
@Requires(bean = Test.class, beanProperty = "name", notEquals = "notEqualValue")
@Requires(bean = Test.class, beanProperty = "num", value = "10")
class RecordDependantBean {}
''')
        def type = context.classLoader.loadClass('test.RecordDependantBean')

        when:
        context.environment.addPropertySource(PropertySource.of("test", ['record.num': 10, 'record.name': 'test']))
        context.getBean(type)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test requires primitive properties with default values"() {
        given:
        ApplicationContext context = buildContext('test.PrimitivesDependantBean', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@ConfigurationProperties("test")
class PrimitiveConfig
{
    int intProperty;
    int anotherIntProperty;
    boolean boolProperty;

    public int getIntProperty() {
        return intProperty;
    }

    public void setIntProperty(int intProperty) {
        this.intProperty = intProperty;
    }

    public int getAnotherIntProperty() {
        return anotherIntProperty;
    }

    public void setAnotherIntProperty(int anotherIntProperty) {
        this.anotherIntProperty = anotherIntProperty;
    }

    public boolean isBoolProperty() {
        return boolProperty;
    }

    public void setBoolProperty(boolean boolProperty) {
        this.boolProperty = boolProperty;
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

    void "test requires not configuration properties"() {
        given:
        ApplicationContext context = buildContext('test.NotConfigPropertiesDependantBean', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.Singleton;

@Singleton
class NotConfigurationProperties
{
    int intProperty;
    boolean boolProperty;
    @Property(name = "test.string-property", defaultValue = "disabled")
    String stringProperty;

    public int getIntProperty() {
        return intProperty;
    }

    public void setIntProperty(int intProperty) {
        this.intProperty = intProperty;
    }

    public boolean isBoolProperty() {
        return boolProperty;
    }

    public void setBoolProperty(boolean boolProperty) {
        this.boolProperty = boolProperty;
    }

    public String getStringProperty() {
        return stringProperty;
    }

    public void setStringProperty(String stringProperty) {
        this.stringProperty = stringProperty;
    }
}

@Singleton
@Requires(bean = NotConfigurationProperties.class, beanProperty = "intProperty", value = "0")
@Requires(bean = NotConfigurationProperties.class, beanProperty = "boolProperty", value = "false")
@Requires(bean = NotConfigurationProperties.class, beanProperty = "stringProperty")
class NotConfigPropertiesDependantBean
{
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
        ApplicationContext context = buildContext('test.TestBean', '''
package test;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationProperties;import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@ConfigurationProperties("test")
class RequiredBean
{
    private Integer intProperty;
    private String stringProperty;

    public Integer getIntProperty() {
        return intProperty;
    }

    public void setIntProperty(Integer intProperty) {
        this.intProperty = intProperty;
    }

    public String getStringProperty() {
        return stringProperty;
    }

    public void setStringProperty(String stringProperty) {
        this.stringProperty = stringProperty;
    }

    @ConfigurationProperties("inner")
    public static class InnerConfig {
        private String innerProperty = "default value";

        public String getInnerProperty() {
            return innerProperty;
        }

        public void setInnerProperty(String innerProperty) {
            this.innerProperty = innerProperty;
        }
    }
}

class TestBean
{
}

@Factory
class TestBeanFactory
{
    @Bean
    @Requires(bean = RequiredBean.class, beanProperty = "intProperty", value = "1")
    @Requires(bean = RequiredBean.class, beanProperty = "stringProperty", value = "enabled")
    public TestBean testBean() {
        return new TestBean();
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
        ApplicationContext context = buildContext('test.TestBean', '''
package test;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationProperties;import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.Toggleable;
import jakarta.inject.Singleton;

interface Configuration extends Toggleable {}

@Singleton
class ConfigurationImpl implements Configuration
{
    private boolean enabled = false;

    @Override
    public boolean isEnabled() {
        return this.enabled;
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
