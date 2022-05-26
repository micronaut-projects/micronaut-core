package io.micronaut.inject.qualifiers.named

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class NamedQualifierSpec extends AbstractTypeElementSpec {

    void "test compile named"() {
        given:
        def context = buildContext('''
package namedcompile;

import jakarta.inject.*;import jakarta.inject.Singleton;

@Singleton
class DrSeuss {
    @Inject @Named("one") 
    public Thing thing1;
    
    @Inject @Named("two") 
    public Thing thing2;
}

interface Thing {
    String getName();
}

@Singleton
@Named("one")
class ThingOne implements Thing {
    @Override public String getName() {
        return "one";
    }
}

@Singleton
@Named("two")
class ThingTwo implements Thing {
    @Override public String getName() {
        return "two";
    }
}
''')

        def bean = getBean(context, "namedcompile.DrSeuss")

        expect:
        bean.thing1.name == 'one'
        bean.thing2.name == 'two'

        cleanup:
        context.close()
    }
}
