package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement

class GroovyDocumentationSpec extends AbstractBeanDefinitionSpec {

    void "test read class level documentation"() {
        def classElement = buildClassElement("""
package test

import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import io.micronaut.ast.groovy.visitor.SuperClass

/**
 * This is class level docs
 */
class Test extends SuperClass {
    /**This is property level docs
     */
    @NotBlank
    @NotNull
    private String tenant

    /**
     * This is method level docs
     */
    String getTenant() {
        return tenant
    }

    /**
        This is method level docs

     */
    void setTenant(String tenant) {
        this.tenant = tenant
    }
}
""")

        expect:
        classElement.getDocumentation().get() == 'This is class level docs'
        classElement.getFields().get(0).getDocumentation().get() == 'This is property level docs'
        classElement.getEnclosedElements(ElementQuery.of(MethodElement.class).named("getTenant")).get(0).getDocumentation().get() == 'This is method level docs'
        classElement.getEnclosedElements(ElementQuery.of(MethodElement.class).named("setTenant")).get(0).getDocumentation().get() == 'This is method level docs'
    }
}
