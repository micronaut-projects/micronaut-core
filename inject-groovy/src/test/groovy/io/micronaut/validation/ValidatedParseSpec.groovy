package io.micronaut.validation

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.aop.Around
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
}
