package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.StaticOriginatingElements
import spock.util.environment.RestoreSystemProperties

class OriginatingElementsSpec extends AbstractTypeElementSpec {

    def cleanup() {
        StaticOriginatingElements.INSTANCE.clear()
    }

    @RestoreSystemProperties
    void "test inject annotation inherited through abstract base"() {
        given:
        System.setProperty("micronaut.static.originating.elements", "true")

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' , '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.*;

@Singleton
class MyBean extends MyBase {

}

abstract class MyBase {
    @Inject
    io.micronaut.core.convert.ConversionService conversionService;
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 1

        and:"the originating elements include the super class"
        StaticOriginatingElements.INSTANCE.originatingElements.size() == 2
        StaticOriginatingElements.INSTANCE.originatingElements[0].name == 'test.MyBean'
        StaticOriginatingElements.INSTANCE.originatingElements[1].name == 'test.MyBase'
    }

    @RestoreSystemProperties
    void "test base class not included if no injection points"() {
        given:
        System.setProperty("micronaut.static.originating.elements", "true")

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' , '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.*;

@Singleton
class MyBean extends MyBase {

}

abstract class MyBase {
    io.micronaut.core.convert.ConversionService conversionService;
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0

        and:"the originating elements include the super class"
        StaticOriginatingElements.INSTANCE.originatingElements.size() == 1
        StaticOriginatingElements.INSTANCE.originatingElements[0].name == 'test.MyBean'
    }

    @RestoreSystemProperties
    void "test executable method inherited through abstract base"() {
        given:
        System.setProperty("micronaut.static.originating.elements", "true")

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' , '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.*;

@Singleton
class MyBean extends MyBase {

}

abstract class MyBase {
    @Executable
    void myMethod() {
        // no-op
    }
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 1

        and:"the originating elements include the super class"
        StaticOriginatingElements.INSTANCE.originatingElements.size() == 2
        StaticOriginatingElements.INSTANCE.originatingElements[0].name == 'test.MyBean'
        StaticOriginatingElements.INSTANCE.originatingElements[1].name == 'test.MyBase'
    }

    @RestoreSystemProperties
    void "test AOP method inherited through abstract base"() {
        given:
        System.setProperty("micronaut.static.originating.elements", "true")

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' , '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import io.micronaut.aop.simple.*;
import jakarta.inject.*;

@Singleton
class MyBean extends MyBase {

}

abstract class MyBase {
    @Mutating("name")
    void myMethod(String name) {
        // no-op
    }
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 1

        and:"the originating elements include the super class"
        StaticOriginatingElements.INSTANCE.originatingElements.size() == 2
        StaticOriginatingElements.INSTANCE.originatingElements[0].name == 'test.MyBean'
        StaticOriginatingElements.INSTANCE.originatingElements[1].name == 'test.MyBase'
    }

    @RestoreSystemProperties
    void "test inject annotation on method inherited through abstract base"() {
        given:
        System.setProperty("micronaut.static.originating.elements", "true")

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' , '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import jakarta.inject.*;
import io.micronaut.core.convert.*;
@Singleton
class MyBean extends MyBase {

}

abstract class MyBase {

    private ConversionService conversionService;

    @Inject
    void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedMethods.size() == 1

        and:"the originating elements include the super class"
        StaticOriginatingElements.INSTANCE.originatingElements.size() == 2
        StaticOriginatingElements.INSTANCE.originatingElements[0].name == 'test.MyBean'
        StaticOriginatingElements.INSTANCE.originatingElements[1].name == 'test.MyBase'
    }

    @RestoreSystemProperties
    void "test originating elements from introduction advise interface inheritance"() {
        given:
        System.setProperty("micronaut.static.originating.elements", "true")

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import java.net.*;
import javax.validation.constraints.*;

interface MyInterface{
    @Executable
    void save(@NotBlank String name, @Min(1L) int age);
    @Executable
    void saveTwo(@Min(1L) String name);
}


@Stub
@jakarta.inject.Singleton
interface MyBean extends MyInterface {
}

''')
        then:
        !beanDefinition.isAbstract()

        and:"the originating elements include the super interface"
        StaticOriginatingElements.INSTANCE.originatingElements.size() == 2
        StaticOriginatingElements.INSTANCE.originatingElements[0].name == 'test.MyBean'
        StaticOriginatingElements.INSTANCE.originatingElements[1].name == 'test.MyInterface'
        beanDefinition.getExecutableMethods().size() == 2
    }

    @RestoreSystemProperties
    void "test originating elements from abstract introduction advise interface inheritance"() {
        given:
        System.setProperty("micronaut.static.originating.elements", "true")

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import java.net.*;
import javax.validation.constraints.*;

interface MyInterface {
    @Executable
    void save(@NotBlank String name, @Min(1L) int age);
    @Executable
    void saveTwo(@Min(1L) String name);
}


@Stub
@jakarta.inject.Singleton
abstract class MyBean implements MyInterface {
}

''')
        then:
        !beanDefinition.isAbstract()

        and:"the originating elements include the super interface"
        StaticOriginatingElements.INSTANCE.originatingElements.size() == 2
        StaticOriginatingElements.INSTANCE.originatingElements[0].name == 'test.MyBean'
        StaticOriginatingElements.INSTANCE.originatingElements[1].name == 'test.MyInterface'

    }

    @RestoreSystemProperties
    void "test originating elements from abstract extended introduction advise interface inheritance"() {
        given:
        System.setProperty("micronaut.static.originating.elements", "true")

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import java.net.*;
import javax.validation.constraints.*;

interface MyInterface {
    @Executable
    void save(@NotBlank String name, @Min(1L) int age);
    @Executable
    void saveTwo(@Min(1L) String name);
}


@Stub
@jakarta.inject.Singleton
abstract class MyBean extends MyParentBean {
}

abstract class MyParentBean implements MyInterface {
}

''')
        then:
        !beanDefinition.isAbstract()

        and:"MyParentBean is not included because it has no bean related annotations"
        StaticOriginatingElements.INSTANCE.originatingElements.size() == 2
        StaticOriginatingElements.INSTANCE.originatingElements[0].name == 'test.MyBean'
        StaticOriginatingElements.INSTANCE.originatingElements[1].name == 'test.MyInterface'

    }
}
