package io.micronaut.inject.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder

class FinishRoundRegisterBeanSpec extends AbstractTypeElementSpec {

    void setup() {
        RoundsVisitor.reset()
    }

    void "test finishRound is called after each round and registers beans of a compiled type"() {
        given:
        def context = buildContext('registerbean.Trigger', '''
package registerbean;

class Trigger {
}

class Other {
}
''')

        expect:"javac finishes the visitors every round, after the round is finished"
        RoundsVisitor.EVENTS.findAll { it.startsWith('round') } == [
                'round1[registerbean.Other, registerbean.Trigger]',
                'round2[registerbean.GeneratedLater]'
        ]
        RoundsVisitor.EVENTS[0..3] == [
                'round1[registerbean.Other, registerbean.Trigger]',
                'finish',
                'round2[registerbean.GeneratedLater]',
                'finish'
        ]

        and:"a bean is registered for each round, with what the round contained"
        context.getBeansOfType(RegisteredRecord).size() == 3
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('round1'))
                .stringValues(Recorded, 'classes') as List == ['registerbean.Other', 'registerbean.Trigger']
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('round2'))
                .stringValues(Recorded, 'classes') as List == ['registerbean.GeneratedLater']
        context.getBean(RegisteredRecord, Qualifiers.byName('round2')) instanceof RegisteredRecord

        and:"a bean registered from finish is written as well"
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('finish'))
                .stringValue(Recorded, 'phase').get() == 'finish'

        and:"the definitions are named after the first originating element"
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('round2')).getClass().name.startsWith('registerbean.$GeneratedLater$RegisteredRecord')

        cleanup:
        context.close()
    }

    void "test a bean cannot be registered without an originating element"() {
        given:
        def beanType = ClassElement.of(RegisteredRecord)

        when:
        AbstractBeanDefinitionBuilder.requireOriginatingElements(beanType)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Bean [io.micronaut.inject.visitor.rounds.RegisteredRecord] cannot be registered without an originating element'
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new RoundsVisitor()]
    }
}
