package io.micronaut.inject.factory.beanmethod

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.type.TypeInformation
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Singleton

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
        BeanDefinition<?> bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar1'))
                .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

                .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

        def bar1 = getBean(context, 'test.Bar1')
        def bar2 = getBean(context, 'test.Bar2')

        then:
        bar1BeanDefinition.getBeanDescription(TypeInformation.TypeFormat.SHORTENED) == '@i.m.c.a.Prototype t.Bar1 t.TestFactory.bar()'
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
            bar2BeanDefinition.getScope().get() == Singleton.class
            bar2BeanDefinition.singleton
            bar2BeanDefinition.declaredQualifier == null
            bar2BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 1
            bar2BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).iterator().next() == AnnotationUtil.SINGLETON
        and:
            !bar3BeanDefinition.getScope().isPresent()
            bar3BeanDefinition.declaredQualifier.toString() == "@Xyz"
            bar3BeanDefinition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).size() == 0
        and:
            bar4BeanDefinition.getScope().get() == Singleton.class
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

    void "factory method access rights"() {
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
import java.time.LocalTime;
import java.util.Arrays;

@Factory
class TestFactory {
    @Bean
    @Xyz
    public static String val1() {
        return "val1";
    }

    @Bean
    @Xyz
    static Integer val2() {
        return 123;
    }

    @Bean
    @Xyz
    protected static Double val3() {
        return 789d;
    }

    @Bean
    @Xyz
    @ReflectiveAccess
    private static LocalTime val4() {
        return LocalTime.MIDNIGHT;
    }

    @Bean
    @Xyz
    public Boolean val5() {
        return Boolean.TRUE;
    }

    @Bean
    @Xyz
    BigDecimal val6() {
        return BigDecimal.TEN;
    }

    @Bean
    @Xyz
    protected BigInteger val7() {
        return BigInteger.ONE;
    }

    @Bean
    @Xyz
    @ReflectiveAccess
    private LocalDate val8(@Xyz String val1, @Xyz Integer val2, @Xyz Double val3, @Xyz LocalTime val4,
                  @Xyz Boolean val5, @Xyz BigDecimal val6, @Xyz BigInteger val7,
                  @Named int val9, @Named int[] val10, @Named int val11) {
              if (!val1.equals("val1") || !val2.equals(123) || !val3.equals(789d) || !val4.equals(LocalTime.MIDNIGHT)
            || !val5.equals(Boolean.TRUE) || !val6.equals(BigDecimal.TEN)
             || !val7.equals(BigInteger.ONE)
             || val9 != 999 || !Arrays.equals(val10, new int[] {1, 2, 3})
             || val11 != 333
             ) {
            throw new RuntimeException();
        }
        return LocalDate.MAX;
    }

    @Bean
    @Named
    @ReflectiveAccess
    private int val9() {
        return 999;
    }

    @Bean
    @Named
    @ReflectiveAccess
    private int[] val10() {
        return new int[] {1, 2, 3};
    }

    @Bean
    @Named
    @ReflectiveAccess
    private static int val11() {
        return 333;
    }

    @Bean
    @Named
    @ReflectiveAccess
    private static int[] val12(@Xyz String val1, @Xyz Integer val2, @Xyz Double val3, @Xyz LocalTime val4,
                                @Xyz Boolean val5, @Xyz BigDecimal val6, @Xyz BigInteger val7, @Xyz LocalDate val8,
                                 @Named int val9, @Named int[] val10, @Named int val11) {
                    if (!val1.equals("val1") || !val2.equals(123) || !val3.equals(789d) || !val4.equals(LocalTime.MIDNIGHT)
            || !val5.equals(Boolean.TRUE) || !val6.equals(BigDecimal.TEN)
             || !val7.equals(BigInteger.ONE) || !val8.equals(LocalDate.MAX)
             || val9 != 999 || !Arrays.equals(val10, new int[] {1, 2, 3})
             || val11 != 333
             ) {
            throw new RuntimeException();
        }
        return new int[]{4, 5, 6};
    }

}

@Singleton
class MyBean {

    public MyBean(@Xyz String val1, @Xyz Integer val2, @Xyz Double val3, @Xyz LocalTime val4,
                  @Xyz Boolean val5, @Xyz BigDecimal val6, @Xyz BigInteger val7, @Xyz LocalDate val8,
                  @Named int val9, @Named int[] val10, @Named int val11, @Named int[] val12) {
        if (!val1.equals("val1") || !val2.equals(123) || !val3.equals(789d) || !val4.equals(LocalTime.MIDNIGHT)
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
