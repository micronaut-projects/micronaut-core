package io.micronaut.validation

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.aop.Around
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.ProxyBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class ValidatedParseSpec extends AbstractBeanDefinitionSpec {
    void "test constraints on beans make them @Validated"() {
        given:
        def definition = buildBeanDefinition('test.$TestDefinition' + BeanDefinitionVisitor.PROXY_SUFFIX,'''
package test;

@javax.inject.Singleton
class Test {

    @io.micronaut.context.annotation.Executable
    public void setName(@javax.validation.constraints.NotBlank String name) {
    
    }
    
    @io.micronaut.context.annotation.Executable
    public void setName2(@javax.validation.Valid String name) {
    
    }
}
''')

        expect:
        definition instanceof ProxyBeanDefinition
        definition.findMethod("setName", String).get().hasStereotype(Validated)
        definition.findMethod("setName2", String).get().getAnnotationTypesByStereotype(Around).contains(Validated)
    }

    void "test annotation default values on a groovy property"() {
        given:
        BeanIntrospection beanIntrospection = buildBeanIntrospection('test.Test','''
package test;

import io.micronaut.core.annotation.Introspected
import javax.validation.Constraint
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Introspected
class Test {

    @ValidURLs
    List<String> webs
}

@Constraint(validatedBy = [])
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface ValidURLs {
    String message() default "invalid url"
}

''')

        expect:
        beanIntrospection.getProperty("webs").isPresent()
        beanIntrospection.getRequiredProperty("webs", List).annotationMetadata.getDefaultValue("test.ValidURLs", "message", String).get() == "invalid url"
    }
}
