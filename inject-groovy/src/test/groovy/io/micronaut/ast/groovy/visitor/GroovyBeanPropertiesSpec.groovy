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

    void "test groovy and java records"() {
        def classElement = buildClassElement("""
package test

import io.micronaut.ast.groovy.visitor.GroovyRecord
import io.micronaut.sample.EmptyRecord
import io.micronaut.sample.JavaRecord 

class ObjectWithProps {

    private GroovyRecord groovyRecord
    private JavaRecord javaRecord
    private EmptyRecord emptyRecord
    
    GroovyRecord getGroovyRecord() {
        return groovyRecord
    }

    void setGroovyRecord(GroovyRecord groovyRecord) {
        this.groovyRecord = groovyRecord
    }

    JavaRecord getJavaRecord() {
        return javaRecord
    }

    void setJavaRecord(JavaRecord javaRecord) {
        this.javaRecord = javaRecord
    }

    EmptyRecord getEmptyRecord() {
        return emptyRecord
    }

    void setEmptyRecord(EmptyRecord emptyRecord) {
        this.emptyRecord = emptyRecord
    }
}

""")

        var props = classElement.getBeanProperties()

        expect:
        props.size() == 3
        props[0].type.isRecord()
        props[1].type.isRecord()
        props[2].type.isRecord()

        when:
        var props1 = props[0].type.getBeanProperties()

        then:
        props1.size() == 2

        when:
        var props2 = props[1].type.getBeanProperties()

        then:
        props2.size() == 2

        when:
        var props3 = props[2].type.getBeanProperties()

        then:
        props3.size() == 0
    }
}
