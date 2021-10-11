package io.micronaut.inject.qualifiers.repeatable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.context.exceptions.NonUniqueBeanException

class RepeatableQualifierSpec extends AbstractTypeElementSpec {

    void "test repeatable qualifiers"() {
        given:
        def context = buildContext('''
package repeatablequalifiers;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
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
class OtherBean {
    @Inject
    @Location("south")
    public Coordinate south;
}

@Factory
class CoordinateFactory {
    @Singleton
    @Location("north")
    @Location("south")
    Coordinate northSouth() {
        return () -> "North/South";
    }
    
    @Singleton
    @Location("south")
    Coordinate South() {
        return () -> "South";
    }
    
    @Singleton
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

        cleanup:
        context.close()
    }
}
