package io.micronaut.python.annotation.processing.test

import spock.lang.PendingFeature

class OptionalPropertySpec extends AbstractPythonTypeElementSpec {

    void "test get bean with constructor optionals not present"() {
        given:
        def context = buildContext(constructorOptionalPropertiesCode())

        when:
        def bean = getBean(context, "python.BeanWithConstructorOptionals")

        then:
        !bean.optional_double_from_constructor_present()
        !bean.optional_int_from_constructor_present()
        !bean.optional_long_from_constructor_present()

        cleanup:
        context?.close()
    }

    void "test get bean with constructor optionals present"() {
        given:
        def context = buildContext(constructorOptionalPropertiesCode(), false, [
            "long.prop": Long.MAX_VALUE,
            "double.prop": "10.5",
            "integer.prop": "10"
        ])

        when:
        def bean = getBean(context, "python.BeanWithConstructorOptionals")

        then:
        bean.optional_double_from_constructor_present()
        bean.optional_int_from_constructor_present()
        bean.optional_long_from_constructor_present()
        bean.optional_int_from_constructor_value() == 10
        bean.optional_double_from_constructor_value() == 10.5d
        bean.optional_long_from_constructor_value() == Long.MAX_VALUE.toString()

        cleanup:
        context?.close()
    }

    void "test get bean with field optionals not present"() {
        given:
        def context = buildContext(fieldOptionalPropertiesCode())

        when:
        def bean = getBean(context, "python.BeanWithFieldOptionals")

        then:
        !bean.optional_double_present()
        !bean.optional_int_present()
        !bean.optional_long_present()

        cleanup:
        context?.close()
    }

    void "test get bean with field optionals present"() {
        given:
        def context = buildContext(fieldOptionalPropertiesCode(), false, [
            "long.prop": Long.MAX_VALUE,
            "double.prop": "10.5",
            "integer.prop": "10",
            "string.prop": "good"
        ])

        when:
        def bean = getBean(context, "python.BeanWithFieldOptionals")

        then:
        bean.string_optional_present()
        bean.string_optional_value() == "good"
        bean.optional_double_present()
        bean.optional_int_present()
        bean.optional_long_present()
        bean.optional_int_value() == 10
        bean.optional_double_value() == 10.5d
        bean.optional_long_value() == Long.MAX_VALUE.toString()

        cleanup:
        context?.close()
    }

    private static String constructorOptionalPropertiesCode() {
        '''
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable, Property

import java

OptionalInt = java.type("java.util.OptionalInt")
OptionalLong = java.type("java.util.OptionalLong")
OptionalDouble = java.type("java.util.OptionalDouble")

@Singleton
class BeanWithConstructorOptionals:
    def __init__(
        self,
        optional_int_from_constructor: Annotated[OptionalInt, Property(name="integer.prop")],
        optional_long_from_constructor: Annotated[OptionalLong, Property(name="long.prop")],
        optional_double_from_constructor: Annotated[OptionalDouble, Property(name="double.prop")]
    ):
        self.optional_int_from_constructor = optional_int_from_constructor
        self.optional_long_from_constructor = optional_long_from_constructor
        self.optional_double_from_constructor = optional_double_from_constructor

    @Executable
    def optional_int_from_constructor_present(self) -> bool:
        return self.optional_int_from_constructor.isPresent()

    @Executable
    def optional_int_from_constructor_value(self) -> int:
        return self.optional_int_from_constructor.getAsInt()

    @Executable
    def optional_long_from_constructor_present(self) -> bool:
        return self.optional_long_from_constructor.isPresent()

    @Executable
    def optional_long_from_constructor_value(self) -> str:
        return str(self.optional_long_from_constructor.getAsLong())

    @Executable
    def optional_double_from_constructor_present(self) -> bool:
        return self.optional_double_from_constructor.isPresent()

    @Executable
    def optional_double_from_constructor_value(self) -> float:
        return self.optional_double_from_constructor.getAsDouble()
'''
    }

    private static String fieldOptionalPropertiesCode() {
        '''
from typing import Annotated, Optional
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable, Property

import java

OptionalInt = java.type("java.util.OptionalInt")
OptionalLong = java.type("java.util.OptionalLong")
OptionalDouble = java.type("java.util.OptionalDouble")

@Singleton
class BeanWithFieldOptionals:
    string_optional: Annotated[Optional[str], Property(name="string.prop")] = None
    optional_int: Annotated[OptionalInt, Property(name="integer.prop")] = None
    optional_long: Annotated[OptionalLong, Property(name="long.prop")] = None
    optional_double: Annotated[OptionalDouble, Property(name="double.prop")] = None

    @Executable
    def string_optional_present(self) -> bool:
        return self.string_optional.isPresent()

    @Executable
    def string_optional_value(self) -> str:
        return self.string_optional.get()

    @Executable
    def optional_int_present(self) -> bool:
        return self.optional_int.isPresent()

    @Executable
    def optional_int_value(self) -> int:
        return self.optional_int.getAsInt()

    @Executable
    def optional_long_present(self) -> bool:
        return self.optional_long.isPresent()

    @Executable
    def optional_long_value(self) -> str:
        return str(self.optional_long.getAsLong())

    @Executable
    def optional_double_present(self) -> bool:
        return self.optional_double.isPresent()

    @Executable
    def optional_double_value(self) -> float:
        return self.optional_double.getAsDouble()
'''
    }
}
