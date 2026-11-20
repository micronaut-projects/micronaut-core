package io.micronaut.inject.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaFileObjects
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder

import javax.tools.Diagnostic

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

        and:"the compilation is finished once, in the final round, before the visitors finish"
        RoundsVisitor.EVENTS.count { it.startsWith('compilation') } == 1
        RoundsVisitor.EVENTS[-2..-1] == [
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

    void "test the beans registered once every round is over are written without a warning"() {
        given:
        RoundsVisitor.setRegisterInFinish(false)

        when:
        def parser = newJavaParser()
        def files = parser.generate(JavaFileObjects.forSourceString('registerbean.Trigger', '''
package registerbean;

class Trigger {
}
'''))
        def warnings = parser.diagnosticCollector.diagnostics
                .findAll { it.kind == Diagnostic.Kind.WARNING || it.kind == Diagnostic.Kind.MANDATORY_WARNING }
                .collect { it.getMessage(Locale.ENGLISH) }

        then:"the definition is written in the final round"
        RoundsVisitor.EVENTS[-2] == 'compilation[registerbean.GeneratedLater, registerbean.Trigger]'
        files.any { it.name.endsWith('$RegisteredRecord1$Definition.class') }

        and:"javac does not warn that it is not processed"
        !warnings.any { it.contains('last round') }

        cleanup:
        parser?.close()
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
