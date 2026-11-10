package io.micronaut.reflection

import io.micronaut.core.annotation.Introspected
import spock.lang.Specification

import java.lang.annotation.ElementType

/**
 * The accessor of a record component is synthetic beside the component field, as the processor marks it.
 */
class ReflectiveSyntheticMemberSpec extends Specification {

    void "the accessor of a record component read reflectively is synthetic beside the component field"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(SyntheticMemberPoint,
                Set.of(Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD))
        def x = introspection.getRequiredProperty('x', int).members

        expect:
        x*.elementType as Set == [ElementType.FIELD, ElementType.METHOD] as Set
        x.find { it.elementType == ElementType.METHOD }.synthetic
        !x.find { it.elementType == ElementType.FIELD }.synthetic

        and: 'a getter of no component is not synthetic'
        introspection.getRequiredProperty('doubled', int).members.every { !it.synthetic }
    }

    void "no member of a plain class is synthetic"() {
        expect:
        ReflectionBeanIntrospection.of(MemberParityBean).beanProperties.every { p -> p.members.every { !it.synthetic } }
    }
}
