package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class RepeatableQualifierSpec extends AbstractPythonTypeElementSpec {

    void "test constructor injection with repeatable qualifiers"() {
        given:
        def context = buildContext(repeatableQualifierCode())

        when:
        def navigator = getBean(context, "python.Navigator")

        then:
        navigator.north_south_name() == "North/South"
        navigator.north_name() == "North/South"
        navigator.east_west_name() == "East/West"

        when:
        getBean(context, "python.SouthOnlyNavigator")

        then:
        def e = thrown(DependencyInjectionException)
        e.cause instanceof NonUniqueBeanException

        cleanup:
        context?.close()
    }

    void "test repeatable qualifier metadata participates in bean lookup"() {
        given:
        def context = buildContext(repeatableQualifierCode())
        def coordinateType = context.classLoader.loadClass("python.Coordinate")

        when:
        BeanDefinition<?> northSouth = context.getBeanDefinition(coordinateType, Qualifiers.byName("northSouth"))
        BeanDefinition<?> south = context.getBeanDefinition(coordinateType, Qualifiers.byName("south"))
        BeanDefinition<?> eastWest = context.getBeanDefinition(coordinateType, Qualifiers.byName("eastWest"))

        then:
        repeatableLocationValues(northSouth) == ["north", "south"] as Set
        repeatableLocationValues(south) == ["south"] as Set
        repeatableLocationValues(eastWest) == ["east", "west"] as Set

        and:
        northSouth.declaredQualifier.toString().contains("@Named('northSouth')")
        northSouth.declaredQualifier.toString().contains("@Location(value=north)")
        northSouth.declaredQualifier.toString().contains("@Location(value=south)")
        south.declaredQualifier.toString().contains("@Named('south')")
        south.declaredQualifier.toString().contains("@Location(value=south)")
        eastWest.declaredQualifier.toString().contains("@Named('eastWest')")
        eastWest.declaredQualifier.toString().contains("@Location(value=east)")
        eastWest.declaredQualifier.toString().contains("@Location(value=west)")

        and:
        context.findBean(coordinateType, northSouth.declaredQualifier).isPresent()
        context.findBean(coordinateType, south.declaredQualifier).isPresent()
        context.findBean(coordinateType, eastWest.declaredQualifier).isPresent()

        cleanup:
        context?.close()
    }

    private static Set<String> repeatableLocationValues(BeanDefinition<?> definition) {
        definition
            .annotationMetadata
            .getAnnotationValuesByName(Location.name)
            .collect { it.stringValue().get() } as Set
    }

    private static String repeatableQualifierCode() {
        '''
from typing import Annotated
from jakarta.inject import Singleton, Named
from micronaut.context.annotation import Executable
from io.micronaut.python.annotation.processing.test.inject import Location

class Coordinate:
    @Executable
    def get_name(self) -> str:
        return "Coordinate"

@Singleton
@Named("northSouth")
@Location("north")
@Location("south")
class NorthSouth(Coordinate):
    @Executable
    def get_name(self) -> str:
        return "North/South"

@Singleton
@Named("south")
@Location("south")
class South(Coordinate):
    @Executable
    def get_name(self) -> str:
        return "South"

@Singleton
@Named("eastWest")
@Location("east")
@Location("west")
class EastWest(Coordinate):
    @Executable
    def get_name(self) -> str:
        return "East/West"

@Singleton
class Navigator:
    def __init__(
        self,
        north_south: Annotated[Coordinate, Location("north"), Location("south")],
        north: Annotated[Coordinate, Location("north")],
        east_west: Annotated[Coordinate, Location("east"), Location("west")]
    ):
        self.north_south = north_south
        self.north = north
        self.east_west = east_west

    @Executable
    def north_south_name(self) -> str:
        return self.north_south.get_name()

    @Executable
    def north_name(self) -> str:
        return self.north.get_name()

    @Executable
    def east_west_name(self) -> str:
        return self.east_west.get_name()

@Singleton
class SouthOnlyNavigator:
    def __init__(self, south: Annotated[Coordinate, Location("south")]):
        self.south = south
'''
    }
}
