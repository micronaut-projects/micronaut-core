package io.micronaut.inject.autowired

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.context.exceptions.NoSuchBeanException

class AutowiredSpec extends AbstractTypeElementSpec {

    void "test autowired required=true (the default)"() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.Autowired;
import jakarta.inject.Singleton;

@Singleton
class Test {
    @Autowired
    public Foo foo;
}

@Singleton
class Foo {

}
''')
        def bean = getBean(context, 'test.Test')

        expect:
        bean.foo != null
    }

    void "test autowired required=true (the default) - failure case"() {
        when:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.Autowired;
import jakarta.inject.Singleton;

@Singleton
class Test {
    @Autowired
    public Foo foo;
}

class Foo {

}
''')
        def bean = getBean(context, 'test.Test')

        then:
        thrown(DependencyInjectionException)
    }

    void "test autowired required=false on field (optional injection)"() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.Autowired;
import jakarta.inject.Singleton;

@Singleton
class Test {
    @Autowired(required = false)
    public Foo foo = new Foo("test");
}

record Foo(String name) {
}
''')
        def bean = getBean(context, 'test.Test')

        expect:
        bean.foo.name == 'test'
    }

    void "test autowired required=false on field value (optional injection)"() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.Autowired;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class Test {
    @Autowired(required = false)
    @Value("${foo.bar}")
    public String value = "unchanged";
}

class Foo {

}
''')
        def bean = getBean(context, 'test.Test')

        expect:
        bean.value == "unchanged"
    }

    void "test autowired required=false on method (optional injection)"() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.Autowired;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class Test {

    private Foo value = new Foo("unchanged");

    @Autowired(required = false)
    public void setValue(Foo value) {
        this.value = value;
    }

    public test.Foo getValue() {
        return value;
    }
}

record Foo(String name) {

}
''')
        def bean = getBean(context, 'test.Test')

        expect:
        bean.value.name == 'unchanged'
    }

    void "test autowired required=false on method (optional injection) - multiple arguments"() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.Autowired;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class Test {

    private Foo value = new Foo("unchanged");

    @Autowired(required = false)
    public void setValues(Bar bar, Foo value) {
        this.value = value;
    }

    public test.Foo getValue() {
        return value;
    }
}

record Foo(String name) {

}

@Singleton
class Bar {

}
''')
        def bean = getBean(context, 'test.Test')

        expect:"If any of the arguments don't resolve then don't invoke"
        bean.value.name == 'unchanged'
    }
}
