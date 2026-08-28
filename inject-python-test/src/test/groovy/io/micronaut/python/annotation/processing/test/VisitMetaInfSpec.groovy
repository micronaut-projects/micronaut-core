package io.micronaut.python.annotation.processing.test

import io.micronaut.python.annotation.processing.test.visitor.AllElementsVisitor

class VisitMetaInfSpec extends AbstractPythonTypeElementSpec {

    void "test visitMetaInfFile writes resource"() {
        given:
        AllElementsVisitor.WRITE_FILE = true
        AllElementsVisitor.WRITE_IN_METAINF = true

        when:
        def definition = buildBeanDefinition('python', 'Test', '''
from jakarta.inject import Singleton

@Singleton
class Test:
    def myMethod(self):
        pass
''')

        then:
        definition != null
        def res = definition.class.classLoader.getResource('META-INF/foo/bar.txt')
        assert res != null
        res.text == 'All good'
    }
}
