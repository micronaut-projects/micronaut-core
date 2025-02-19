package io.micronaut.inject.factory.beanfield

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.type.TypeInformation
import io.micronaut.core.util.CollectionUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Unroll

class FactoryBeanFieldSpec extends AbstractTypeElementSpec {

    void "test fail compilation for AOP advice for primitive array type from field"() {
        when:
        buildBeanDefinition('primitive.fields.factory.errors.PrimitiveFactory',"""
package primitive.fields.factory.errors;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import io.micronaut.aop.simple.Mutating;

@Factory
class PrimitiveFactory {
    @Bean
    @Named("totals")
    @Mutating("test")
    int[] totals = { 10 };
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Cannot apply AOP advice to arrays")
    }

    void "test fail compilation for AOP advice to primitive type from field"() {
        when:
        buildBeanDefinition('primitive.fields.factory.errors.PrimitiveFactory',"""
package primitive.fields.factory.errors;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import io.micronaut.aop.simple.Mutating;

@Factory
class PrimitiveFactory {
    @Bean
    @Named("total")
    @Mutating("test")
    int totals = 10;
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Cannot apply AOP advice to primitive beans")
    }

    void "test fail compilation when defining preDestroy for primitive type from field"() {
        when:
        buildBeanDefinition('primitive.fields.factory.errors.PrimitiveFactory',"""
package primitive.fields.factory.errors;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import io.micronaut.aop.simple.Mutating;

@Factory
class PrimitiveFactory {
    @Bean(preDestroy="close")
    @Named("total")
    int totals = 10;
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Using 'preDestroy' is not allowed on primitive type beans")
    }

    @Unroll
    void "test produce bean for primitive #primitiveType array type from field"() {
        given:
        def context = buildContext("""
package primitive.fields.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
class PrimitiveFactory {
    @Bean
    @Named("totals")
    $primitiveType[] totals = { 10 };
}

@Singleton
class MyBean {
    public final $primitiveType[] totals;

    @Inject
    @Named("totals")
    public $primitiveType[] totalsFromField;

    public $primitiveType[] totalsFromMethod;

    MyBean(@Named $primitiveType[] totals) {
        this.totals = totals;
    }

    @Inject
    void setTotals(@Named $primitiveType[] totals) {
        this.totalsFromMethod = totals;
    }
}
""")

        def bean = getBean(context, 'primitive.fields.factory.MyBean')

        expect:
        bean.totals[0] == 10
        bean.totalsFromField[0] == 10
        bean.totalsFromMethod[0] == 10

        where:
        primitiveType << ['int', 'short', 'long', 'double', 'float', 'byte']
    }

    @Unroll
    void "test produce bean for primitive #primitiveType matrix array type from field"() {
        given:
        def context = buildContext("""
package primitive.fields.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
class PrimitiveFactory {
    @Bean
    @Named("totals")
    $primitiveType[][] totals = { new $primitiveType[] { 10 } };
}

@Singleton
class MyBean {
    public final $primitiveType[][] totals;

    @Inject
    @Named("totals")
    public $primitiveType[][] totalsFromField;

    public $primitiveType[][] totalsFromMethod;

    MyBean(@Named $primitiveType[][] totals) {
        this.totals = totals;
    }

    @Inject
    void setTotals(@Named $primitiveType[][] totals) {
        this.totalsFromMethod = totals;
    }
}
""")

        def bean = getBean(context, 'primitive.fields.factory.MyBean')

        expect:
        bean.totals[0][0] == 10
        bean.totalsFromField[0][0] == 10
        bean.totalsFromMethod[0][0] == 10

        where:
        primitiveType << ['int', 'short', 'long', 'double', 'float', 'byte']
    }

    @Unroll
    void "test produce bean for primitive #primitiveType type from field"() {
        given:
        def context = buildContext("""
package primitive.fields.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
class PrimitiveFactory {
    @Bean
    @Named("total")
    $primitiveType total = 10;
}

@Singleton
class MyBean {
    public final $primitiveType total;

    @Inject
    @Named("total")
    public $primitiveType totalFromField;

    public $primitiveType totalFromMethod;

    MyBean(@Named $primitiveType total) {
        this.total = total;
    }

    @Inject
    void setTotal(@Named $primitiveType total) {
        this.totalFromMethod = total;
    }
}
""")

        def bean = getBean(context, 'primitive.fields.factory.MyBean')

        expect:
        bean.total == 10
        bean.totalFromField == 10

        where:
        primitiveType << ['int', 'short', 'long', 'double', 'float', 'byte']
    }

    void "test a factory bean can be supplied from a field"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestField', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import io.micronaut.inject.factory.enummethod.TestEnum;
import jakarta.inject.*;
import java.util.Locale;
import jakarta.inject.Singleton;

@Factory
class TestFactory$TestField {

    @Singleton
    @Bean
    @io.micronaut.context.annotation.Primary
    Foo one = new Foo("one");

    // final fields are implicitly singleton
    @Bean
    @Named("two")
    final Foo two = new Foo("two");

    // non-final fields are prototype
    @Bean
    @Named("three")
    Foo three = new Foo("three");

    @SomeMeta
    @Bean
    Foo four = new Foo("four");

    @Bean
    @Mutating
    Bar bar = new Bar();
}

class Bar {
    public String test(String test) {
        return test;
    }
}

class Foo {
    final String name;
    Foo(String name) {
        this.name = name;
    }
}

@Retention(RUNTIME)
@Singleton
@Named("four")
@AroundConstruct
@interface SomeMeta {
}

@Retention(RUNTIME)
@Singleton
@Around
@interface Mutating {
}

@Singleton
@InterceptorBean(SomeMeta.class)
class TestConstructInterceptor implements ConstructorInterceptor<Object> {
    boolean invoked = false;
    Object[] parameters;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        invoked = true;
        parameters = context.getParameterValues();
        return context.proceed();
    }
}

@InterceptorBean(Mutating.class)
class TestInterceptor implements MethodInterceptor<Object, Object> {
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) {
        final Object[] parameterValues = context.getParameterValues();
        parameterValues[0] = parameterValues[0].toString().toUpperCase(Locale.ENGLISH);
        System.out.println(parameterValues[0]);
        return context.proceed();
    }
}
''')

        def barBean = getBean(context, 'test.Bar')
        def definition = context.getBeanDefinition(context.classLoader.loadClass('test.Foo'))

        expect:
        definition.getBeanDescription(TypeInformation.TypeFormat.SHORTENED) == '@i.m.c.a.Primary @j.i.Singleton t.Foo t.T$TestField.one'
        barBean.test("good") == 'GOOD' // proxied
        getBean(context, "test.Foo").name == 'one'
        getBean(context, "test.Foo", Qualifiers.byName("two")).name == 'two'
        getBean(context, "test.Foo", Qualifiers.byName("two")).is(
                getBean(context, "test.Foo", Qualifiers.byName("two"))
        )
        getBean(context, "test.Foo", Qualifiers.byName("three")).is(
                getBean(context, "test.Foo", Qualifiers.byName("three"))
        )
        getBean(context, 'test.TestConstructInterceptor').invoked == false
        getBean(context, "test.Foo", Qualifiers.byName("four")) // around construct
        getBean(context, 'test.TestConstructInterceptor').invoked == true

        cleanup:
        context.close()
    }

    @Unroll
    void 'test fail compilation on invalid modifier #modifier'() {
        when:
        buildBeanDefinition('invalidmod.TestFactory', """
package invalidmod;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Factory
class TestFactory {
    @Bean
    $modifier Test test;
}

class Test {}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("cannot be ")
        e.message.contains(modifier)

        where:
        modifier << ['private']
    }

    void "if a factory field bean defines Bean and Prototype scopes and the original bean type scope is Singleton BeanDefinition getScope returns Prototype and BeanDefinition getAnnotationNamesByStereotype for jakarta.inject.Scope returns Prototype The original qualifier is not present if the factory field bean does not define a Qualifier"() {

        given:
        ApplicationContext context = buildContext('test.TestFactory$TestField', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;

@Some
@Factory
class TestFactory$TestField {

    @Bean
    @Prototype
    Bar1 bar = new Bar1();
}

@Abc
@Singleton
class Bar1 {
}

@Retention(RUNTIME)
@Qualifier
@interface Abc {
}

@Retention(RUNTIME)
@Qualifier
@interface Some {
}

''')

        when:
        BeanDefinition bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar1'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }
        then:
        bar1BeanDefinition.getScope().get() == Prototype.class
        bar1BeanDefinition.declaredQualifier == null
        bar1BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1

        when:
        Collection<String> annotationNamesByScopeStereoType = bar1BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).asCollection()

        then:
        annotationNamesByScopeStereoType.any {it == "io.micronaut.context.annotation.Prototype" }

        cleanup:
        context.close()
    }

    void "if a factory field bean defines a @Bean and @Singleton scopes, BeanDefinition::getScope return empty but BeanDefinition::getAnnotationNamesByStereotype for jakarta.inject.Scope returns jakarta.inject.Singleton The original qualifier is not present if the factory field bean does not define a Qualifier"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestField', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;

@Some
@Factory
class TestFactory$TestField {
    @Bean
    @Singleton
    Bar2 bar2 = new Bar2();
}

@Abc
class Bar2 {
}

@Retention(RUNTIME)
@Qualifier
@interface Abc {
}

@Retention(RUNTIME)
@Qualifier
@interface Some {
}
''')
        when:
        BeanDefinition bar2BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar2'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }

        then:
        bar2BeanDefinition.getScope().get() == Singleton.class
        bar2BeanDefinition.singleton
        bar2BeanDefinition.declaredQualifier == null
        bar2BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
        bar2BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).iterator().next() == AnnotationUtil.SINGLETON

        cleanup:
        context.close()
    }

    void "if a factory field bean defines a @Bean scope and the original type did not have any scope, BeanDefinition::getScope and BeanDefinition::getAnnotationNamesByStereotype for jakarta.inject.Scope return empty. if factory field bean defines a qualifier, the original Qualifier is overridden"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestField', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;

@Some
@Factory
class TestFactory$TestField {

    @Bean
    @Xyz
    Bar3 bar3 = new Bar3();
}

@Abc
class Bar3 {
}

@Retention(RUNTIME)
@Qualifier
@interface Abc {
}

@Retention(RUNTIME)
@Qualifier
@interface Xyz {
}

@Retention(RUNTIME)
@Qualifier
@interface Some {
}

''')
        when:
        BeanDefinition bar3BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar3'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }

        then:
        !bar3BeanDefinition.getScope().isPresent()
        bar3BeanDefinition.declaredQualifier.toString() == "@Xyz"
        bar3BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 0

        cleanup:
        context.close()
    }

    void "if factory field bean defines a @Bean scope and no qualifier, the original Scope and Qualifier are used"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestField', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;

@Some
@Factory
class TestFactory$TestField {

    @Bean
    Bar4 bar4 = new Bar4();
}

@Abc
@Singleton
class Bar4 {
}

@Retention(RUNTIME)
@Qualifier
@interface Abc {
}

@Retention(RUNTIME)
@Qualifier
@interface Some {
}

''')
        when:
        BeanDefinition bar4BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar4'))
                .find { it.getDeclaringType().get().simpleName.contains("TestFactory") }

        then:
        bar4BeanDefinition.getScope().get() == Singleton.class
        bar4BeanDefinition.singleton
        bar4BeanDefinition.declaredQualifier.toString() == "@Abc"
        bar4BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
        bar4BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).iterator().next() == AnnotationUtil.SINGLETON

        cleanup:
        context.close()
    }

    void "if factory field bean defines a @Bean scope, BeanDefinition::getScope and BeanDefinition::getAnnotationNamesByStereotype for jakarta.inject.Scope return empty, even if original bean defines @Singleton If factory field bean defines a qualifier, the original Qualifier is overridden"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestField', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;

@Some
@Factory
class TestFactory$TestField {
    @Bean
    @Xyz
    Bar5 bar5 = new Bar5();
}

@Abc
@Singleton
class Bar5 {
}

@Retention(RUNTIME)
@Qualifier
@interface Abc {
}

@Retention(RUNTIME)
@Qualifier
@interface Xyz {
}

@Retention(RUNTIME)
@Qualifier
@interface Some {
}

''')
        when:
        BeanDefinition bar5BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar5'))
                .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

        then:
        !bar5BeanDefinition.getScope().isPresent()
        bar5BeanDefinition.declaredQualifier.toString() == "@Xyz"
        CollectionUtils.isEmpty(bar5BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE))

        cleanup:
        context.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/pull/7165")
    void "if factory field bean defines a @Bean and @Prototype scopes, BeanDefinition::getScope returns Prototype and BeanDefinition::getAnnotationNamesByStereotype for jakarta.inject.Scope returns Prototype If factory field bean defines a qualifier, the original Qualifier is overridden"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestField', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;

@Some
@Factory
class TestFactory$TestField {

    @Bean
    @Xyz
    @Prototype
    Bar6 bar6 = new Bar6();
}

@Abc
@Singleton
class Bar6 {
}

@Retention(RUNTIME)
@Qualifier
@interface Abc {
}

@Retention(RUNTIME)
@Qualifier
@interface Xyz {
}

@Retention(RUNTIME)
@Qualifier
@interface Some {
}

''')
        when:
        BeanDefinition bar6BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar6'))
                .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}
        then:
        bar6BeanDefinition.getScope().get() == Prototype.class
        bar6BeanDefinition.declaredQualifier.toString() == "@Xyz"

        when:
        Collection<String> annotationNamesByScopeStereoType = bar6BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).asCollection()

        then:
        annotationNamesByScopeStereoType.size() == 1
        annotationNamesByScopeStereoType.any {it == "io.micronaut.context.annotation.Prototype" }

        cleanup:
        context.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/pull/7165")
    void "if factory has a qualifier it is inherited by the factory field beans"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestField', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;

@Some
@Factory
class TestFactory$TestField {
    @Prototype
    Bar7 bar7 = new Bar7();
}

class Bar7 {
}

@Retention(RUNTIME)
@Qualifier
@interface Some {
}

''')
        when:
        BeanDefinition bar7BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar7'))
                .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}
        then:
        bar7BeanDefinition.getScope().get() == Prototype.class
        bar7BeanDefinition.declaredQualifier == null

        when:
        Collection<String> annotationNamesByScopeStereoType = bar7BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).asCollection()

        then:
        annotationNamesByScopeStereoType.size() == 1
        annotationNamesByScopeStereoType.any {it == "io.micronaut.context.annotation.Prototype" }

        cleanup:
        context.close()
    }

    void "newly introduced annotation should not break inherited qualifier/scope"() {
        given:
            ApplicationContext context = buildContext('test.TestFactory$TestField', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;

@Some
@Factory
class TestFactory$TestField {

    @io.micronaut.inject.factory.RemappedAnnotation
    @Bean
    @Xyz
    @Prototype
    Bar7 bar7 = new Bar7();

    @Bean
    @Xyz
    @Prototype
    Bar8 bar8 = new Bar8();
}

@Abc
@Singleton
class Bar7 {
}

@Abc
@Singleton
@io.micronaut.inject.factory.RemappedAnnotation
class Bar8 {
}

@Retention(RUNTIME)
@Qualifier
@interface Abc {
}

@Retention(RUNTIME)
@Qualifier
@interface Xyz {
}

@Retention(RUNTIME)
@Qualifier
@interface Some {
}

''')
        when:
            def bar7BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar7'))
                    .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

            def bar8BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar8'))
                    .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

        then:
            bar7BeanDefinition.getScope().get() == Prototype.class
            bar7BeanDefinition.declaredQualifier.toString() == "@Xyz"
            bar7BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
            bar7BeanDefinition.hasAnnotation(io.micronaut.inject.factory.RemappedAnnotation)
        and:
            bar8BeanDefinition.getScope().get() == Prototype.class
            bar8BeanDefinition.declaredQualifier.toString() == "@Xyz"
            bar8BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
            bar8BeanDefinition.hasAnnotation(io.micronaut.inject.factory.RemappedAnnotation)

        cleanup:
        context.close()
    }


    void "test preDestroy continues if an exception is thrown in the disposal method"() {
        given:
        ApplicationContext context = buildContext('test.Implementation2', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;import java.util.concurrent.atomic.AtomicInteger;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Factory
class FactoryClass {

   @Bean(preDestroy = "close")
   @Singleton
   Interface build() {
       return new Implementation1();
   }

    @Bean(preDestroy="close")
    @Singleton
    Test testBean() {
        return new Test();
    }
}

class Test {
    public static AtomicInteger closed = new AtomicInteger(0);

    public void close() {
        closed.incrementAndGet();
        throw new RuntimeException("Test");
    }
}

interface Interface {
    void close();
}

interface SubInterface extends Interface {
}

class Implementation1 implements SubInterface {
    public static AtomicInteger closed = new AtomicInteger(0);

    @Override
    public void close() {
        closed.incrementAndGet();
        throw new RuntimeException("Implementation 1");
    }
}

@Singleton
public class Implementation2 implements Interface {
    public static AtomicInteger closed = new AtomicInteger(0);

    @Override
    public void close() {
        closed.incrementAndGet();
        throw new RuntimeException("Implementation 2");
    }
}
''')
        when:
        def implementation = getBean(context, 'test.Interface')
        def test = getBean(context, 'test.Test')

        def implementation2Class = context.classLoader.loadClass('test.Implementation2')

        then:
        implementation.class.simpleName == "Implementation1"
        implementation.closed.intValue() == 0

        and:
        implementation2Class.getDeclaredField('closed').get(null).intValue() == 0

        and:
        test.class.simpleName == "Test"
        test.closed.intValue() == 0

        when:
        try { context.close() } catch(e) { println "Caught $e.message" }

        then: "the non-factory bean wasn't closed"
        implementation2Class.getDeclaredField('closed').get(null).intValue() == 0

        and: 'the factory beans were all closed'
        implementation.closed.intValue() == 1
        test.closed.intValue() == 1
    }

    void "factory that is also a producer"() {
        given:
            ApplicationContext context = buildContext('test.TestFactory', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;

@Factory
class TestFactory {
    @Bean
    @Xyz
    public static String VAL1 = "val1";
    @Bean
    @Xyz
    public static Integer VAL2 = 123;

    @Bean
    @Xyz
    public static Double val3producer() {
        return 876d;
    }

    public TestFactory(@Xyz String val1, @Xyz Integer val2, @Xyz Double val3) {
        if (!val1.equals(VAL1) || !val2.equals(VAL2) || !val3.equals(val3producer())) {
            throw new RuntimeException();
        }
    }
}

@Retention(RUNTIME)
@Qualifier
@interface Xyz {
}

''')
        when:
            BeanDefinition factoryBeanDefinition = context.getBeanDefinition(context.classLoader.loadClass('test.TestFactory'))

        then:
            context.getBeanRegistration(factoryBeanDefinition).bean

        cleanup:
            context.close()
    }

    void "factory field access rights"() {
        given:
            ApplicationContext context = buildContext('test.TestFactory', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;
import java.math.*;
import java.time.LocalDate;
import java.util.Arrays;

@Factory
class TestFactory {
    @Bean
    @Xyz
    public static String VAL1 = "val1";

    @Bean
    @Xyz
    static Integer VAL2 = 123;

    @Bean
    @Xyz
    protected static Double VAL3 = 789d;

    @Bean
    @Xyz
    @ReflectiveAccess
    private static Float VAL4 = 744f;

    @Bean
    @Xyz
    public Boolean VAL5 = Boolean.TRUE;

    @Bean
    @Xyz
    BigDecimal VAL6 = BigDecimal.TEN;

    @Bean
    @Xyz
    protected BigInteger VAL7 = BigInteger.ONE;

    @Bean
    @Xyz
    @ReflectiveAccess
    private LocalDate VAL8 = LocalDate.MAX;

    @Bean
    @Named
    @ReflectiveAccess
    private int VAL9 = 999;

    @Bean
    @Named
    @ReflectiveAccess
    private int[] VAL10 = {1, 2, 3};

    @Bean
    @Named
    @ReflectiveAccess
    private static int VAL11 = 333;

    @Bean
    @Named
    @ReflectiveAccess
    private static int[] VAL12 = {4, 5, 6};

}

@Singleton
class MyBean {

    public MyBean(@Xyz String val1, @Xyz Integer val2, @Xyz Double val3, @Xyz Float val4,
                  @Xyz Boolean val5, @Xyz BigDecimal val6, @Xyz BigInteger val7, @Xyz LocalDate val8,
                  @Named int val9, @Named int[] val10, @Named int val11, @Named int[] val12) {
        if (!val1.equals("val1") || !val2.equals(123) || !val3.equals(789d) || !val4.equals(744f)
            || !val5.equals(Boolean.TRUE) || !val6.equals(BigDecimal.TEN)
             || !val7.equals(BigInteger.ONE) || !val8.equals(LocalDate.MAX)
             || val9 != 999 || !Arrays.equals(val10, new int[] {1, 2, 3})
             || val11 != 333 || !Arrays.equals(val12, new int[] {4, 5, 6})
             ) {
            throw new RuntimeException();
        }
    }
}

@Retention(RUNTIME)
@Qualifier
@interface Xyz {
}

''')
        when:
            BeanDefinition beanDefinition = context.getBeanDefinition(context.classLoader.loadClass('test.MyBean'))

        then:
            context.getBeanRegistration(beanDefinition).bean

        cleanup:
            context.close()
    }
}
