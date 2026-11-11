package io.micronaut.kotlin.processing.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.rounds.Recorded
import io.micronaut.inject.visitor.rounds.RegisteredRecord
import io.micronaut.inject.visitor.rounds.RoundsVisitor

class FinishRoundRegisterBeanSpec extends AbstractKotlinCompilerSpec {

    void setup() {
        RoundsVisitor.reset()
    }

    void "test finishRound is called after each round and registers beans of a compiled type"() {
        given:
        RoundsVisitor.setRegisterInFinish(false)
        def context = buildContext('''
package registerbean

class Trigger

class Other
''')

        expect:"the generated source is a round of its own, and the compilation and the visitors finish once"
        RoundsVisitor.EVENTS == [
                'round1[registerbean.Other, registerbean.Trigger]',
                'round2[registerbean.GeneratedLater]',
                'compilation[registerbean.GeneratedLater, registerbean.Other, registerbean.Trigger]',
                'finish'
        ]

        and:"a bean is registered for each round, with what the round contained"
        context.getBeansOfType(RegisteredRecord).size() == 3
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('round1'))
                .stringValues(Recorded, 'classes') as List == ['registerbean.Other', 'registerbean.Trigger']
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('round2'))
                .stringValues(Recorded, 'classes') as List == ['registerbean.GeneratedLater']
        context.getBean(RegisteredRecord, Qualifiers.byName('round2')) instanceof RegisteredRecord

        and:"a bean registered once every round is over sees the classes of every round"
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('compilation'))
                .stringValues(Recorded, 'classes') as List == ['registerbean.GeneratedLater', 'registerbean.Other', 'registerbean.Trigger']
        context.getBean(RegisteredRecord, Qualifiers.byName('compilation')) instanceof RegisteredRecord

        and:"the definitions are named after the first originating element"
        context.getBeanDefinition(RegisteredRecord, Qualifiers.byName('round2')).getClass().name.startsWith('registerbean.$GeneratedLater$RegisteredRecord')

        cleanup:
        context.close()
    }

    void "test a bean cannot be registered once the visitors finish"() {
        when:
        buildContext('''
package registerbean

class Trigger
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Bean [io.micronaut.inject.visitor.rounds.RegisteredRecord] cannot be added once the visitors finish')
    }
}
