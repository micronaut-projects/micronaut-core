package io.micronaut.inject.visitor.beans.builder

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.exceptions.IntrospectionException
import org.junit.jupiter.api.Test
import spock.lang.Specification

import static org.junit.jupiter.api.Assertions.assertEquals

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

    void 'test build type with import'() {
        given:
        def introspection = BeanIntrospection.getIntrospection(TestBuildMe5)
        def builder = introspection.builder()
        def result = builder.with("name", "Fred").with("age", 20).build()


        expect:
        introspection.hasBuilder()
        introspection.isBuildable()
        result.name == "Fred"
        result.age == 20
    }

    void 'test build type with import, no builder declaration'() {
        when:
        def introspection = BeanIntrospection.getIntrospection(TestBuildMe6)
        def builder = introspection.builder()
        def result = builder.with("name", "Fred").with("age", 20).build()


        then:
        thrown(IntrospectionException)
    }

    void 'test build type with builder method'() {

        given:
        def introspection = BeanIntrospection.getIntrospection(TestBuildMe2)
        def builder = introspection.builder()
        def result = builder.with("name", "Fred").with("age", 20).build()


        expect:
        introspection.instantiate("Fred", 20) == result
        introspection.constructorArguments.length == 2
        introspection.hasBuilder()
        introspection.isBuildable()
        result.name == "Fred"
        result.age == 20
    }

    void 'test build type with builder method with arguments'() {

        given:
        def introspection = BeanIntrospection.getIntrospection(TestBuildMe3)
        def builder = introspection.builder()
        def result = builder.with("name", "Fred").with("age", 20).build("Apple")


        expect:
        result.name == "Fred"
        result.age == 20
        result.company == "Apple"
        introspection.constructorArguments.length == 3
        introspection.instantiate("Fred", 20, "Apple") == result
        introspection.hasBuilder()
        introspection.isBuildable()
    }

    void 'test build type with builder class and custom creator method'() {
        given:
        def introspection = BeanIntrospection.getIntrospection(TestBuildMe4)
        def builder = introspection.builder()
        def result = builder.with("name", "Fred").with("age", 20).build()


        expect:
        introspection.hasBuilder()
        introspection.isBuildable()
        result.name == "Fred"
        result.age == 20
    }

    void 'test build type with builder that has private constructor'() {
        given:
        def introspection = BeanIntrospection.getIntrospection(TestBuildMe7)
        def builder = introspection.builder()
        def result = builder.with("name", "Fred").with("age", 20).build()


        expect:
        introspection.hasBuilder()
        introspection.isBuildable()
        result.name == "Fred"
        result.age == 20
    }

    void 'test build type with builder that has protected constructor and different target package'() {
        given:
        def introspection = BeanIntrospection.getIntrospection(TestBuildMe8)
        def builder = introspection.builder()
        def result = builder.with("name", "Fred").with("age", 20).build()


        expect:
        introspection.hasBuilder()
        introspection.isBuildable()
        result.name == "Fred"
        result.age == 20
    }

    void "test super builder"() {
        given:
        BeanIntrospection<SubBuilder> introspection = BeanIntrospection.getIntrospection(SubBuilder.class)
        BeanIntrospection.Builder<SubBuilder> builder = introspection.builder()
        SubBuilder sub = builder
                .with("foo", "fizz")
                .with("bar", "buzz")
                .build()

        expect:
        sub.toString() == new SubBuilder.Builder().bar("buzz").foo("fizz").build().toString()
    }
}
