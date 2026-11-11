package io.micronaut.inject.provider.lookuppoint

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class ProviderLookupResolutionSpec extends AbstractTypeElementSpec {

    void "a prototype a provider creates later can depend on the prototype the provider was injected into"() {
        given:
        ApplicationContext context = buildContext('''
package test;

import jakarta.inject.Provider;
import io.micronaut.context.annotation.Prototype;

@Prototype
class A {
    final Provider<B> b;
    A(Provider<B> b) {
        this.b = b;
    }
}

@Prototype
class B {
    final A a;
    B(A a) {
        this.a = a;
    }
}
''')
        def a = getBean(context, 'test.A')

        when:
        def b = a.b.get()

        then:
        b != null
        b.a != null
        !b.a.is(a)

        cleanup:
        context.close()
    }

    void "a BeanProvider injected into the constructor of an implementation lists that implementation later"() {
        given:
        ApplicationContext context = buildContext('''
package test;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

interface Foo {
}

@Singleton
class FooOne implements Foo {
    final BeanProvider<Foo> foos;
    FooOne(BeanProvider<Foo> foos) {
        this.foos = foos;
    }

    List<String> names() {
        return foos.stream().map(f -> f.getClass().getSimpleName()).sorted().collect(Collectors.toList());
    }
}

@Singleton
class FooTwo implements Foo {
}
''')
        def one = getBean(context, 'test.FooOne')

        expect:
        one.names() == ['FooOne', 'FooTwo']

        cleanup:
        context.close()
    }
}
