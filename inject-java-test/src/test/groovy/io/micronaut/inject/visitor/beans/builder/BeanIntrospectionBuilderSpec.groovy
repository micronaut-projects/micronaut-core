package io.micronaut.inject.visitor.beans.builder

import io.micronaut.core.beans.BeanIntrospection
import spock.lang.Specification

class BeanIntrospectionBuilderSpec extends Specification {

    void 'test build record type'() {

        given:
        def introspection = BeanIntrospection.getIntrospection(TestRecord)
        def builder = introspection.builder()

        when:
        def result = builder.with("name", "Fred").with("age", 20).build()

        then:
        !introspection.hasBuilder()
        introspection.isBuildable()
        result == new TestRecord("Fred", 20)

        when:
        result = introspection.builder().with(result).with("age", 10)
            .build()

        then:
        result == new TestRecord("Fred", 10)
    }

    void 'test build java bean type'() {

        given:
        def introspection = BeanIntrospection.getIntrospection(TestBean)
        def builder = introspection.builder()

        when:
        !introspection.hasBuilder()
        introspection.isBuildable()
        def result = builder.with("name", "Fred").with("age", 20).with("nickNames", ["Freddy"]).build()
        def expected = new TestBean("Fred")
        expected.age = 20
        expected.nickNames = ['Freddy']

        then:
        result == expected
    }

    void 'test build type with builder class'() {
        given:
        def introspection = BeanIntrospection.getIntrospection(TestBuildMe)
        def builder = introspection.builder()
        def result = builder.with("name", "Fred").with("age", 20).build()


        expect:
        introspection.hasBuilder()
        introspection.isBuildable()
        result.name == "Fred"
        result.age == 20
    }

    void 'test build type with builder method'() {

        given:
        def introspection = BeanIntrospection.getIntrospection(TestBuildMe2)
        def builder = introspection.builder()
        def result = builder.with("name", "Fred").with("age", 20).build()


        expect:
        introspection.hasBuilder()
        introspection.isBuildable()
        result.name == "Fred"
        result.age == 20
    }
}
