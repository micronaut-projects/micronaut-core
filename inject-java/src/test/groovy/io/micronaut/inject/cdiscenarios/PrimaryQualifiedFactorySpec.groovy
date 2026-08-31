package io.micronaut.inject.cdiscenarios

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class PrimaryQualifiedFactorySpec extends AbstractTypeElementSpec {

    void "a factory that is primary as well as qualified can be resolved by the bean it produces"() {
        given:
        def context = buildContext('test.Thing', '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Marker {
}

class Thing {
    final String name;
    Thing(String name) {
        this.name = name;
    }
}

@Factory
@Singleton
@Primary
@Marker
class Things {
    @Bean
    @Prototype
    Thing thing() {
        return new Thing("made");
    }
}
''')

        when:
        def thing = context.getBean(context.classLoader.loadClass('test.Thing'))

        then:
        thing.name == 'made'

        cleanup:
        context.close()
    }
}
