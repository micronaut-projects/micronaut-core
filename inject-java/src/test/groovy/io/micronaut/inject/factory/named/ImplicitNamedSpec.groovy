package io.micronaut.inject.factory.named

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil

class ImplicitNamedSpec extends AbstractTypeElementSpec {

    void 'test implicit named on type'() {
        given:
        def definition = buildBeanDefinition('implicitnamed.FooBar', '''
package implicitnamed;

import io.micronaut.context.annotation.*;
import jakarta.inject.Named;

@Named
class FooBar {}
''')
        expect:
        definition.stringValue(AnnotationUtil.NAMED).get() == 'fooBar'
    }

    void 'test implicit named on type via stereotype'() {
        given:
        def definition = buildBeanDefinition('implicitnamed.FooBar', '''
package implicitnamed;

import io.micronaut.context.annotation.*;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;

@Meta
@Singleton
class FooBar {}

@Named
@Retention(RUNTIME)
@interface Meta {

}
''')
        expect:
        definition.stringValue(AnnotationUtil.NAMED).get() == 'fooBar'
    }

    void 'test use of implicit @Named annotation'() {
        given:
        def context = buildContext('''
package implicitnamed;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Factory
class TestFactory {
    
    @Singleton
    @Named
    Foo foo1() {
        return ()-> "one";
    }
    
    @Singleton
    @Primary
    @Named
    Foo fooPrimary() {
        return ()-> "primary";
    }
    
    @Singleton
    @Named
    Foo getFooGetter() {
        return ()-> "getter";
    }
}

class Test {
    @Named
    @Inject
    public Foo foo1;
    
    @Named("foo1")
    @Inject
    public Foo anotherFoo1;
    
    @Inject
    public Foo fooDefault;
    
    @Named
    @Inject
    public Foo fooPrimary;
    
    @Named("fooGetter")
    @Inject
    public Foo getter;
    
    Foo foo1Ctor; 
    Foo foo1Method;
    Test(@Named Foo foo1) {
        foo1Ctor = foo1;
    }
    
    @Inject
    void setFoo(@Named Foo foo1) {
        foo1Method = foo1;
    }
}

interface Foo {
    String name();
}
        ''')

        expect:
        context != null

        def bean = getBean(context, 'implicitnamed.Test')
        bean.foo1.name() == 'one'
        bean.anotherFoo1.name() == 'one'
        bean.fooDefault.name() == 'primary'
        bean.fooPrimary.name() == 'primary'
        bean.foo1Ctor.name() == 'one'
        bean.foo1Method.name() == 'one'
        bean.getter.name() == 'getter'
        cleanup:
        context.close()
    }
}
