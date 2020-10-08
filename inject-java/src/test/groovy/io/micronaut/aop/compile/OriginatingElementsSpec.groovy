package io.micronaut.aop.compile

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.StaticOriginatingElements
import spock.util.environment.RestoreSystemProperties

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

class OriginatingElementsSpec extends AbstractTypeElementSpec {
    @RestoreSystemProperties
    void "test inject annotation inherited through abstract base"() {
        given:
        System.setProperty("micronaut.static.originating.elements", "true")

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' , '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import javax.inject.*;

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
    void "test inject annotation on method inherited through abstract base"() {
        given:
        System.setProperty("micronaut.static.originating.elements", "true")

        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' , '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import javax.inject.*;
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
@javax.inject.Singleton
interface MyBean extends MyInterface {
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
@javax.inject.Singleton
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
}
