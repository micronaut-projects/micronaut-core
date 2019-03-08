package io.micronaut.validation.properties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class MixedCasePropertySpec extends AbstractTypeElementSpec {

    void "test wrong property name in @Property"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Property;
import javax.inject.Singleton;

@Singleton
class MyService {

    @Property(name = "fooBar")
    private String property;
}

""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Value 'fooBar' used in @Property is not valid. Please use kebab-case notation.")
    }

    void "test wrong property name in @Value"() {
        when:
            buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import javax.inject.Singleton;

@Singleton
class MyService {

    @Value(\"\${fooBar:baz}\")
    private String property;
}

""")
        then:
            def e = thrown(RuntimeException)
            e.message.contains("Value 'fooBar' used in @Value is not valid. Please use kebab-case notation.")
    }
}
