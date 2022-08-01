package io.micronaut.inject.qualifiers.repeatable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

class RepeatableQualifierSpec extends AbstractTypeElementSpec {

    void "test repeatable qualifiers"() {
        given:
        def context = buildContext('''
package repeatablequalifiers;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;
import io.micronaut.context.BeanRegistration;import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import jakarta.inject.Named;
import io.micronaut.context.annotation.*;

@Singleton
class TestBean {
    @Inject
    @Location("north")
    @Location("south")
    public Coordinate northSouth;

    @Inject
    @Location("north")
    public Coordinate north;

    @Inject
    @Location("east")
    @Location("west")
    public Coordinate eastWest;
}

@Singleton
class TestBeanDef1 {
    @Inject
    @Named("northSouth")
    public BeanRegistration<Coordinate> northSouth;

    @Inject
    @Named("south")
    public BeanRegistration<Coordinate> south;

    @Inject
    @Named("eastWest")
    public BeanRegistration<Coordinate> eastWest;
}

@Singleton
class TestBeanDef2 {
    @Inject
    @Location("north")
    @Location("south")
    public BeanRegistration<Coordinate> northSouth;

    @Inject
    @Named("south")
    @Location("south")
    public BeanRegistration<Coordinate> south;

    @Inject
    @Location("east")
    @Location("west")
    public BeanRegistration<Coordinate> eastWest;
}

@Singleton
class OtherBean {
    @Inject
    @Location("south")
    public Coordinate south;
}

@Factory
class CoordinateFactory {
    @Singleton
    @Named
    @Location("north")
    @Location("south")
    Coordinate northSouth() {
        return () -> "North/South";
    }

    @Singleton
    @Named
    @Location("south")
    Coordinate South() {
        return () -> "South";
    }

    @Singleton
    @Named
    @Location("east")
    @Location("west")
    Coordinate eastWest() {
        return () -> "East/West";
    }

}


interface Coordinate {
    String getName();
}

@Retention(RUNTIME)
@Qualifier
@Repeatable(Locations.class)
@interface Location {
    String value();
}

@Retention(RUNTIME)
@interface Locations {
    Location[] value();
}
''')
        when:
        def bean = getBean(context, 'repeatablequalifiers.TestBean')

        then:
        bean.northSouth.name == 'North/South'
        bean.north.name == 'North/South'
        bean.eastWest.name == 'East/West'

        when:
        getBean(context, 'repeatablequalifiers.OtherBean')

        then:
        def e = thrown(DependencyInjectionException)
        e.cause instanceof NonUniqueBeanException

        when:
        def beanDef1 = getBean(context, 'repeatablequalifiers.TestBeanDef1')
        BeanDefinition eastWest = beanDef1.eastWest.beanDefinition
        BeanDefinition northSouth = beanDef1.northSouth.beanDefinition
        BeanDefinition south = beanDef1.south.beanDefinition

        then:
        eastWest.declaredQualifier.toString() == "@Named('eastWest') and @Location(value=east) and @Location(value=west)"
        northSouth.declaredQualifier.toString() == "@Named('northSouth') and @Location(value=north) and @Location(value=south)"
        south.declaredQualifier.toString() == "@Named('south') and @Location(value=south)"

        when:
        def beanDef2 = getBean(context, 'repeatablequalifiers.TestBeanDef2')
        eastWest = beanDef2.eastWest.beanDefinition
        northSouth = beanDef2.northSouth.beanDefinition
        south = beanDef2.south.beanDefinition

        then:
        eastWest.declaredQualifier.toString() == "@Named('eastWest') and @Location(value=east) and @Location(value=west)"
        northSouth.declaredQualifier.toString() == "@Named('northSouth') and @Location(value=north) and @Location(value=south)"
        south.declaredQualifier.toString() == "@Named('south') and @Location(value=south)"

        and:
        context.findBean(eastWest.asArgument(), eastWest.declaredQualifier).isPresent()
        context.findBean(northSouth.asArgument(), northSouth.declaredQualifier).isPresent()
        context.findBean(south.asArgument(), south.declaredQualifier).isPresent()

        context.findBean(eastWest.asArgument(), Qualifiers.byName("eastWest")).isPresent()
        context.findBean(northSouth.asArgument(), Qualifiers.byName("northSouth")).isPresent()
        context.findBean(south.asArgument(), Qualifiers.byName("south")).isPresent()

        cleanup:
        context.close()
    }
}
