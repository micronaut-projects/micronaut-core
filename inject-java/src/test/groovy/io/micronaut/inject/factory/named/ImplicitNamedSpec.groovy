package io.micronaut.inject.factory.named

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class ImplicitNamedSpec extends AbstractTypeElementSpec {

    void 'test use of implicit @Named annotation'() {
        given:
        def context = buildContext('''
package implicitnamed;

import io.micronaut.context.annotation.*;
import javax.inject.*;

@Factory
class TestFactory {
    
    @Singleton
    Foo foo1() {
        return ()-> "one";
    }
    
    @Singleton
    @Primary
    Foo fooPrimary() {
        return ()-> "primary";
    }
    
}

class Test {
    @Named
    public Foo foo1;
    
    @Named("foo1")
    public Foo foo1;
    
    @Inject
    public Foo fooDefault;
    
    @Named
    public Foo fooPrimary;
    
}

interface Foo {
    String name();
}
        ''')

        expect:
        context != null

        cleanup:
        context.close()
    }
}
