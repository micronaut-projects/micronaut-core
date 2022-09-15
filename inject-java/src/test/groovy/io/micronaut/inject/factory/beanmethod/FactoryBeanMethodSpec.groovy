package io.micronaut.inject.factory.beanmethod

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationUtil

class FactoryBeanMethodSpec extends AbstractTypeElementSpec {

    void "test a factory bean with static method or field"() {
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
    @Prototype
    static Bar1 bar() {
        return new Bar1();
    }

    @Bean
    @Prototype
    static Bar2 bar = new Bar2();
}

class Bar1 {
}

class Bar2 {
}


''')

        when:
        def bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar1'))
                .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

                .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

        def bar1 = getBean(context, 'test.Bar1')
        def bar2 = getBean(context, 'test.Bar2')

        then:
        bar1 != null
        bar2 != null
        bar1BeanDefinition.getScope().get() == Prototype.class

        cleanup:
        context.close()
    }

    void "test a factory method bean with existing scope and qualifier"() {
        given:
            ApplicationContext context = buildContext('test.TestFactory$TestMethod', '''\
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
class TestFactory$TestMethod {

    @Bean
    @Prototype
    Bar1 bar() {
        return new Bar1();
    }

    @Bean
    @Singleton
    Bar2 bar2() {
        return new Bar2();
    }

    @Bean
    @Xyz
    Bar3 bar3() {
        return new Bar3();
    }

    @Bean
    Bar4 bar4() {
        return new Bar4();
    }

    @Bean
    @Xyz
    Bar5 bar5() {
        return new Bar5();
    }

    @Bean
    @Xyz
    @Prototype
    Bar6 bar6() {
        return new Bar6();
    }

    @io.micronaut.inject.factory.RemappedAnnotation
    @Bean
    @Xyz
    @Prototype
    Bar7 bar7() {
        return new Bar7();
    }

    @Bean
    @Xyz
    @Prototype
    Bar8 bar8() {
        return new Bar8();
    }
}

@Abc
@Singleton
class Bar1 {
}

@Abc
class Bar2 {
}

@Abc
class Bar3 {
}

@Abc
@Singleton
class Bar4 {
}

@Abc
@Singleton
class Bar5 {
}

@Abc
@Singleton
class Bar6 {
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
            def bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar1'))
                    .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

            def bar2BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar2'))
                    .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

            def bar3BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar3'))
                    .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

            def bar4BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar4'))
                    .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

            def bar5BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar5'))
                    .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

            def bar6BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar6'))
                    .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

            def bar7BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar7'))
                    .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

            def bar8BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar8'))
                    .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

        then:
            bar1BeanDefinition.getScope().get() == Prototype.class
            bar1BeanDefinition.declaredQualifier == null
            bar1BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
        and:
            !bar2BeanDefinition.getScope().isPresent() // javax.inject.Singleton is not present :-/
            bar2BeanDefinition.singleton
            bar2BeanDefinition.declaredQualifier == null
            bar2BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
            bar2BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).iterator().next() == AnnotationUtil.SINGLETON
        and:
            !bar3BeanDefinition.getScope().isPresent()
            bar3BeanDefinition.declaredQualifier.toString() == "@Xyz"
            bar3BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 0
        and:
            !bar4BeanDefinition.getScope().isPresent()
            bar4BeanDefinition.singleton
            bar4BeanDefinition.declaredQualifier.toString() == "@Abc"
            bar4BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
            bar4BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).iterator().next() == AnnotationUtil.SINGLETON
        and:
            !bar5BeanDefinition.getScope().isPresent()
            bar5BeanDefinition.declaredQualifier.toString() == "@Xyz"
            bar5BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 0
        and:
            bar6BeanDefinition.getScope().get() == Prototype.class
            bar6BeanDefinition.declaredQualifier.toString() == "@Xyz"
            bar6BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
        and:
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
}
