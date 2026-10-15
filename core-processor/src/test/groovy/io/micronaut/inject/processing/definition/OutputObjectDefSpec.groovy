package io.micronaut.inject.processing.definition

import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.writer.OriginatingElements
import io.micronaut.sourcegen.model.ClassDef
import spock.lang.Specification

class OutputObjectDefSpec extends Specification {

    void "object definitions are compared and printed by the content of their service entry"() {
        given:
        def objectDef = ClassDef.builder('test.$A$Definition').build()
        def originatingElements = OriginatingElements.of()
        def definition = new OutputObjectDef(objectDef, BeanDefinitionReference, originatingElements, [1, 2] as byte[])
        def same = new OutputObjectDef(objectDef, BeanDefinitionReference, originatingElements, [1, 2] as byte[])

        expect:
        definition == same
        definition.hashCode() == same.hashCode()
        definition != new OutputObjectDef(objectDef, BeanDefinitionReference, originatingElements, [1, 3] as byte[])
        definition != new OutputObjectDef(objectDef, BeanDefinitionReference, originatingElements)
        new OutputObjectDef(objectDef, null, originatingElements) == new OutputObjectDef(objectDef, null, originatingElements)
        definition.toString().endsWith(', serviceContent=[1, 2]]')
    }
}
