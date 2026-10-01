package io.micronaut.jdt

import io.micronaut.annotation.processing.test.jdt.AbstractJdtTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.BeanDefinition

/**
 * End to end tests that compile beans with the Eclipse JDT compiler and then run them.
 */
class JdtRuntimeSpec extends AbstractJdtTypeElementSpec {

    void "test an @EachProperty record binds every property when @Parameter is not first"() {
        given:
        ApplicationContext context = buildContext('test.UnorderedConfiguration', '''
package test;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

@EachProperty("demos")
public record UnorderedConfiguration(
    @Parameter String name,
    String mode,
    boolean enabled) {
}
''', true, [
                'demos.one.mode'   : 'fast',
                'demos.one.enabled': true,
                'demos.two.mode'   : 'slow',
                'demos.two.enabled': false
        ])

        when:
        def type = context.classLoader.loadClass('test.UnorderedConfiguration')
        def beans = context.getBeansOfType(type).toSorted { a, b -> a.name() <=> b.name() }

        then:
        beans.size() == 2
        beans[0].name() == 'one'
        beans[0].mode() == 'fast'
        beans[0].enabled()
        beans[1].name() == 'two'
        beans[1].mode() == 'slow'
        !beans[1].enabled()

        cleanup:
        context.close()
    }

    void "test a record introspection keeps declaration order"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.ReverseRecord', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record ReverseRecord(String zulu, int yankee, String alpha) {
}
''')

        expect:
        introspection.beanProperties*.name == ['zulu', 'yankee', 'alpha']
        introspection.constructorArguments*.name == ['zulu', 'yankee', 'alpha']

        when:
        def instance = introspection.instantiate('z', 1, 'a')

        then:
        instance.zulu() == 'z'
        instance.yankee() == 1
        instance.alpha() == 'a'
    }

    void "test a nested annotation member survives compilation"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Holder', '''
package test;

import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requirements({
    @Requires(property = "zulu"),
    @Requires(property = "alpha", value = "true")
})
public class Holder {
}
''')

        when:
        def requirements = definition.getAnnotationValuesByType(io.micronaut.context.annotation.Requires)

        then:
        requirements.size() == 2
        requirements[0].stringValue("property").get() == 'zulu'
        requirements[1].stringValue("property").get() == 'alpha'
        requirements[1].stringValue("value").get() == 'true'
    }

    void "test configuration properties binding"() {
        given:
        ApplicationContext context = buildContext('test.AppConfiguration', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("app")
public class AppConfiguration {

    private String zulu;
    private int yankee;
    private List<String> alpha;

    public String getZulu() {
        return zulu;
    }

    public void setZulu(String zulu) {
        this.zulu = zulu;
    }

    public int getYankee() {
        return yankee;
    }

    public void setYankee(int yankee) {
        this.yankee = yankee;
    }

    public List<String> getAlpha() {
        return alpha;
    }

    public void setAlpha(List<String> alpha) {
        this.alpha = alpha;
    }
}
''', true, ['app.zulu': 'z', 'app.yankee': 7, 'app.alpha': ['a', 'b']])

        when:
        def config = getBean(context, 'test.AppConfiguration')

        then:
        config.zulu == 'z'
        config.yankee == 7
        config.alpha == ['a', 'b']

        cleanup:
        context.close()
    }

    void "test around advice is applied and intercepts"() {
        given:
        ApplicationContext context = buildContext('test.Source', '''
package test;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.Around;
import jakarta.inject.Singleton;

import java.lang.annotation.*;

@Around
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@interface Shout {
}

@InterceptorBean(Shout.class)
class ShoutInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Object result = context.proceed();
        return result instanceof String s ? s.toUpperCase() : result;
    }
}

@Singleton
class Greeter {

    @Shout
    public String greet(String name) {
        return "hello " + name;
    }
}
''', true)

        when:
        def greeter = getBean(context, 'test.Greeter')

        then:
        greeter.greet("world") == 'HELLO WORLD'

        cleanup:
        context.close()
    }

    void "test javadoc is carried into the configuration metadata"() {
        given:
        String metadata = buildAndReadResourceAsString(
                'META-INF/spring-configuration-metadata.json', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Documented configuration.
 */
@ConfigurationProperties("documented")
public class Test {

    private String zulu;

    /**
     * The zulu value.
     *
     * @return the zulu
     */
    public String getZulu() {
        return zulu;
    }

    /**
     * Sets the zulu value.
     *
     * @param zulu the zulu
     */
    public void setZulu(String zulu) {
        this.zulu = zulu;
    }
}
''')

        expect:
        metadata.contains('"name":"documented.zulu"')
        metadata.contains('"description":"Documented configuration."')
        metadata.contains('"description":"Sets the zulu value."')
    }

    void "test enum introspection instantiates by name"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Mode', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public enum Mode {
    ZULU,
    ALPHA,
    MIKE
}
''')

        expect:
        introspection.constructorArguments.length == 1
        introspection.constructorArguments[0].name == 'name'
        introspection.instantiate('ALPHA').name() == 'ALPHA'
    }
}
