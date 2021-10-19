package io.micronaut.inject.factory.named

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class ImplicitNamedSpec extends AbstractBeanDefinitionSpec {
    void 'test use of implicit @Named annotation'() {
        given:
        def context = buildContext('''
package implicitnamed;

import io.micronaut.context.annotation.*;
import javax.inject.*;

@Factory
class TestFactory {
    static final String CONST = "constNamedFoo"
    
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
    @Named("explicitlyNamedFoo")
    Foo explicitNamed() {
        return () -> "explicitlyNamed";
    }
    
    @Singleton
    @Named(TestFactory.CONST)
    Foo constantNamed() {
        return () -> "constNamedFoo";
    }
}

class Test {
    @Named
    @Inject
    public Foo foo1;
    
    @Named("foo1")
    @Inject
    public Foo anotherFoo1;
    
    @Named("explicitlyNamedFoo")
    @Inject
    public Foo explicitlyNamedField;
    
    @Named("explicitlyNamedFoo")
    @Inject
    Foo explicitlyNamedProp
    
    @Named("explicitlyNamedFoo")
    Foo explicitlyNamedProp2
    
    @Named(TestFactory.CONST)
    @Inject
    public Foo constNamedField;
    
    @Named(TestFactory.CONST)
    @Inject
    Foo constNamedProp;
    
    @Inject
    public Foo fooDefault;
    
    @Named
    @Inject
    public Foo fooPrimary;
    
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
        bean.explicitlyNamedField.name() == 'explicitlyNamed'
        bean.explicitlyNamedProp.name() == 'explicitlyNamed'
        bean.explicitlyNamedProp2.name() == 'explicitlyNamed'
        bean.constNamedField.name() == 'constNamedFoo'
        bean.constNamedProp.name() == 'constNamedFoo'

        cleanup:
        context.close()
    }
}
