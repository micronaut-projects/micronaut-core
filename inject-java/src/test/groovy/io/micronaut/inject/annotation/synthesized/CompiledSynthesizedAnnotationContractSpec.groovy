package io.micronaut.inject.annotation.synthesized

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class CompiledSynthesizedAnnotationContractSpec extends AbstractTypeElementSpec {

    void "an annotation synthesized from compiled metadata equals and hashes like the written one: #members"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Test', """
package test;

import io.micronaut.inject.annotation.synthesized.MemberKinds;
import java.util.concurrent.TimeUnit;

@jakarta.inject.Singleton
@MemberKinds($members)
class Test {
}
""")
        MemberKinds written = definition.beanType.getAnnotation(MemberKinds)
        MemberKinds synthesized = definition.synthesize(MemberKinds)

        expect:
        written != null
        synthesized.equals(written)
        written.equals(synthesized)
        synthesized.hashCode() == written.hashCode()

        and: "the nested annotations it answers hash like the written ones too"
        synthesized.nested().hashCode() == written.nested().hashCode()
        Arrays.hashCode(synthesized.nesteds()) == Arrays.hashCode(written.nesteds())

        where:
        members << [
            '',
            'string = "b"',
            'primitive = 7',
            'constant = TimeUnit.DAYS',
            'type = String.class',
            'nested = @MemberKinds.Nested(value = "b", unit = TimeUnit.DAYS)',
            'strings = {"b", "c"}',
            'primitives = {7, 8}',
            'constants = {TimeUnit.DAYS, TimeUnit.HOURS}',
            'types = {String.class, Integer.class}',
            'nesteds = {@MemberKinds.Nested("b"), @MemberKinds.Nested(value = "c", unit = TimeUnit.DAYS)}',
            'strings = {}, primitives = {}, constants = {}, types = {}, nesteds = {}',
            'string = "b", primitive = 7, constant = TimeUnit.DAYS, type = String.class, nested = @MemberKinds.Nested("b"), strings = {"b"}, primitives = {7}, constants = {TimeUnit.DAYS}, types = {String.class}, nesteds = {@MemberKinds.Nested("b")}'
        ]
    }
}
