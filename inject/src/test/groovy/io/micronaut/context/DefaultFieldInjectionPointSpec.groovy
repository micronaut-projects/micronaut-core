package io.micronaut.context

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

class DefaultFieldInjectionPointSpec extends Specification {

    void "test default field injection point reflective set"() {
        given:
        DefaultFieldInjectionPoint dfip = new DefaultFieldInjectionPoint(
                Mock(BeanDefinition),
                Foo,
                String,
                "bar",
                AnnotationMetadata.EMPTY_METADATA,
                Argument.ZERO_ARGUMENTS
        )

        when:
        Foo foo = new Foo()
        dfip.set(foo, "test")

        then:
        dfip.field != null
        dfip.name == 'bar'
        dfip.type == String
        foo.bar == 'test'
    }

    class Foo {
        String bar
    }
}
