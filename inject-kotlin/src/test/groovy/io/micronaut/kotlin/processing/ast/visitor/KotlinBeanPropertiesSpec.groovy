package io.micronaut.kotlin.processing.ast.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class KotlinBeanPropertiesSpec extends AbstractKotlinCompilerSpec {

    void "test kotlin and java records"() {
        def classElement = buildClassElement("test.ObjectWithProps", """
package test

import io.micronaut.sample.EmptyRecord
import io.micronaut.sample.JavaRecord 

open class ObjectWithProps(
    var javaRecord: JavaRecord,
    var emptyRecord: EmptyRecord,
)
""")

        var props = classElement.getBeanProperties()

        expect:
        props.size() == 2
        props[0].type.isRecord()
        props[1].type.isRecord()

        when:
        var props1 = props[0].type.getBeanProperties()

        then:
        props1.size() == 2

        when:
        var props2 = props[1].type.getBeanProperties()

        then:
        props2.size() == 0
    }
}
