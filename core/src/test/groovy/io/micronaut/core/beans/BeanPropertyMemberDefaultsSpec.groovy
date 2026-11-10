package io.micronaut.core.beans

import io.micronaut.core.type.Argument
import spock.lang.Specification

import java.lang.annotation.ElementType

class BeanPropertyMemberDefaultsSpec extends Specification {

    void "a member that does not say otherwise is read directly and is not synthetic"() {
        given:
        BeanPropertyMember<Object, String> member = new PlainMember()

        expect:
        !member.reflectionRequired
        !member.synthetic
    }

    static class PlainMember implements BeanPropertyMember<Object, String> {
        @Override
        String getName() { 'name' }

        @Override
        ElementType getElementType() { ElementType.FIELD }

        @Override
        Class<?> getDeclaringType() { Object }

        @Override
        Argument<String> asArgument() { Argument.STRING }

        @Override
        boolean isReadable() { true }

        @Override
        String read(Object bean) { 'value' }
    }
}
