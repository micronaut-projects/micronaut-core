package io.micronaut.inject.factory.beanmethod

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.factory.RemappedAnnotation

class FactoryBeanMethodSpec extends AbstractTypeElementSpec {

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
        BeanDefinition<?> bar1BeanDefinition = findBeanDefinitionByDeclaringType(context, 'test.Bar1')

        BeanDefinition<?> bar2BeanDefinition = findBeanDefinitionByDeclaringType(context, 'test.Bar2')

        BeanDefinition<?> bar3BeanDefinition = findBeanDefinitionByDeclaringType(context, 'test.Bar3')

        BeanDefinition<?> bar4BeanDefinition = findBeanDefinitionByDeclaringType(context, 'test.Bar4')

        BeanDefinition<?> bar5BeanDefinition = findBeanDefinitionByDeclaringType(context, 'test.Bar5')

        BeanDefinition<?>  bar6BeanDefinition = findBeanDefinitionByDeclaringType(context, 'test.Bar6')

        BeanDefinition<?> bar7BeanDefinition = findBeanDefinitionByDeclaringType(context, 'test.Bar7')

        BeanDefinition<?> bar8BeanDefinition = findBeanDefinitionByDeclaringType(context, 'test.Bar8')

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
        bar3BeanDefinition.declaredQualifier.toString() == "@Named('test.Xyz')"
        bar3BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 0

        and:
        !bar4BeanDefinition.getScope().isPresent()
        bar4BeanDefinition.singleton
        bar4BeanDefinition.declaredQualifier.toString() == "@Abc"
        bar4BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
        bar4BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).iterator().next() == AnnotationUtil.SINGLETON

        and:
        !bar5BeanDefinition.getScope().isPresent()
        bar5BeanDefinition.declaredQualifier.toString() == "@Named('test.Xyz')"
        bar5BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 0
        and:
        bar6BeanDefinition.getScope().get() == Prototype.class
        bar6BeanDefinition.declaredQualifier.toString() == "@Named('test.Xyz')"
        bar6BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1

        and:
        bar7BeanDefinition.getScope().get() == Prototype.class
        bar7BeanDefinition.declaredQualifier.toString() == "@Named('test.Xyz')"
        bar7BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
        bar7BeanDefinition.hasAnnotation(RemappedAnnotation)

        and:
        bar8BeanDefinition.getScope().get() == Prototype.class
        bar8BeanDefinition.declaredQualifier.toString() == "@Named('test.Xyz')"
        bar8BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
        bar8BeanDefinition.hasAnnotation(RemappedAnnotation)

        cleanup:
        context.close()
    }

    private static BeanDefinition<?> findBeanDefinitionByDeclaringType(ApplicationContext context, String name, String declaringTypeSimpleName = "TestFactory") {
        context.getBeanDefinitions(context.classLoader.loadClass(name))
                .find {it.getDeclaringType().get().simpleName.contains(declaringTypeSimpleName)}
    }
}
