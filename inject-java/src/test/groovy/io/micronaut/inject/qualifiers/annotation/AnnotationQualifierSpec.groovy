package io.micronaut.inject.qualifiers.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class AnnotationQualifierSpec extends AbstractTypeElementSpec {
    void "test compile annotation qualifier"() {
        given:
        def context = buildContext('''
package annqualifiercompile;

import jakarta.inject.*;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;


@Singleton
class DrSeuss {
    @Inject @One
    public Thing thing1;
    
    @Inject @Two 
    public Thing thing2;
}

interface Thing {
    String getName();
}

@Singleton
@One
class ThingOne implements Thing {
    @Override public String getName() {
        return "one";
    }
}

@Singleton
@Two
class ThingTwo implements Thing {
    @Override public String getName() {
        return "two";
    }
}

@Retention(RUNTIME)
@Qualifier
@interface One {}

@Retention(RUNTIME)
@Qualifier
@interface Two {}
''')

        def bean = getBean(context, "annqualifiercompile.DrSeuss")

        expect:
        bean.thing1.name == 'one'
        bean.thing2.name == 'two'

        cleanup:
        context.close()
    }
}
