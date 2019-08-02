package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class BuildClassElementSpec extends AbstractTypeElementSpec {

    void "test build class element"() {
        given:
        def element = buildClassElement('''
package test;

class Test {

}
''')
        expect:
        element.name == 'test.Test'
    }
}
