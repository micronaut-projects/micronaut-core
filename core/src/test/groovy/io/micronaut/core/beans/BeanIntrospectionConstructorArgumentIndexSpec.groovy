package io.micronaut.core.beans

import io.micronaut.core.reflect.exception.InstantiationException
import io.micronaut.core.type.Argument
import spock.lang.Specification

import java.lang.annotation.Annotation

class BeanIntrospectionConstructorArgumentIndexSpec extends Specification {

    def "default getConstructorArgument returns the constructor argument with the name"() {
        given:
        def b = Argument.of(String, "b")
        def a = Argument.of(String, "a")
        def i = new ArgsOnlyIntrospection(b, a)

        expect:
        i.getConstructorArgument("b").get().is(b)
        i.getConstructorArgument("a").get().is(a)
        !i.getConstructorArgument("c").isPresent()
        !new ArgsOnlyIntrospection().getConstructorArgument("a").isPresent()
    }

    def "default getConstructorArgument rejects null"() {
        when:
        new ArgsOnlyIntrospection(Argument.of(String, "a")).getConstructorArgument(null)

        then:
        thrown(NullPointerException)
    }

    static class ArgsOnlyIntrospection implements BeanIntrospection<Object> {
        private final Argument<?>[] args

        ArgsOnlyIntrospection(Argument<?>... args) { this.args = args }

        @Override Argument<?>[] getConstructorArguments() { args }
        @Override Collection<BeanProperty<Object, Object>> getBeanProperties() { [] }
        @Override Collection<BeanProperty<Object, Object>> getIndexedProperties(Class<? extends Annotation> annotationType) { [] }
        @Override BeanIntrospection.Builder<Object> builder() { throw new UnsupportedOperationException() }
        @Override Object instantiate() throws InstantiationException { throw new UnsupportedOperationException() }
        @Override Object instantiate(boolean strictNullable, Object... arguments) throws InstantiationException { throw new UnsupportedOperationException() }
        @Override Class<Object> getBeanType() { Object }
        @Override Optional<BeanProperty<Object, Object>> getIndexedProperty(Class<? extends Annotation> annotationType, String annotationValue) { Optional.empty() }

        // Explicitly disambiguate from GroovyObject#getProperty(String), whose Object return type
        // otherwise conflicts with the default BeanIntrospection#getProperty(String) at compile time.
        @Override Optional<BeanProperty<Object, Object>> getProperty(String name) { BeanIntrospection.super.getProperty(name) }
    }

    def "default constructorArgumentIndexOf scans the constructor arguments"() {
        given:
        def i = new ArgsOnlyIntrospection(Argument.of(String, "b"), Argument.of(String, "a"), Argument.of(int, "c"))

        expect:
        i.constructorArgumentIndexOf("b") == 0
        i.constructorArgumentIndexOf("a") == 1
        i.constructorArgumentIndexOf("c") == 2
        i.constructorArgumentIndexOf("d") == -1
        new ArgsOnlyIntrospection().constructorArgumentIndexOf("a") == -1
    }

    def "default constructorArgumentIndexOf rejects null"() {
        when:
        new ArgsOnlyIntrospection(Argument.of(String, "a")).constructorArgumentIndexOf(null)

        then:
        thrown(NullPointerException)
    }
}
