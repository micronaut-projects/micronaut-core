package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

import jakarta.validation.constraints.NotBlank

class GroovyBeanPropertiesSpec extends AbstractBeanDefinitionSpec {

    void "test annotation metadata from superclass fields is included"() {
        def classElement = buildClassElement("""
package test

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import io.micronaut.ast.groovy.visitor.SuperClass

class Test extends SuperClass {
    @NotBlank
    @NotNull
    private String tenant

    String getTenant() {
        return tenant
    }

    void setTenant(String tenant) {
        this.tenant = tenant
    }
}
""")

        expect:
        classElement.getBeanProperties().every { it.hasAnnotation(NotBlank) }
    }
}
