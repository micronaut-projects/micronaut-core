package io.micronaut.inject.visitor.rounds

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.qualifiers.Qualifiers

class FinishRoundRegisterBeanSpec extends AbstractBeanDefinitionSpec {

    void setup() {
        RoundsVisitor.reset()
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, RoundsVisitor.name)
    }

    void cleanup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
    }

    void "test finishRound is called after each round and registers beans of a compiled type"() {
        given:
        def context = buildContext('''
package registerbean

class Trigger {
}

class Other {
}
''')

        expect:"the generated source is a round of its own, and the compilation and the visitors finish once"
        RoundsVisitor.EVENTS == [
                'round1[registerbean.Other, registerbean.Trigger]',
                'round2[registerbean.GeneratedLater]',
                'compilation[registerbean.GeneratedLater, registerbean.Other, registerbean.Trigger]',
                'finish'
        ]

        and:"a bean is registered for each round, with what the round contained"
        context.getBeansOfType(RegisteredRecord).size() == 4
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('round1'))
                .stringValues(Recorded, 'classes') as List == ['registerbean.Other', 'registerbean.Trigger']
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('round2'))
                .stringValues(Recorded, 'classes') as List == ['registerbean.GeneratedLater']
        context.getBean(RegisteredRecord, Qualifiers.byName('round2')) instanceof RegisteredRecord

        and:"a bean registered from finish is written as well"
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('finish'))
                .stringValue(Recorded, 'phase').get() == 'finish'

        and:"a bean registered once every round is over sees the classes of every round"
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('compilation'))
                .stringValues(Recorded, 'classes') as List == ['registerbean.GeneratedLater', 'registerbean.Other', 'registerbean.Trigger']
        context.getBean(RegisteredRecord, Qualifiers.byName('compilation')) instanceof RegisteredRecord

        and:"the definitions are named after the first originating element"
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('round2')).getClass().name.startsWith('registerbean.$GeneratedLater$RegisteredRecord')

        cleanup:
        context.close()
    }
}
