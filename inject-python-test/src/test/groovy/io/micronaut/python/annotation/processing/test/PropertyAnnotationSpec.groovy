package io.micronaut.python.annotation.processing.test

import spock.lang.PendingFeature

class PropertyAnnotationSpec extends AbstractPythonTypeElementSpec {

    void "test inject with property default value on constructor argument"() {
        given:
        def context = buildContext('''
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable, Property

@Singleton
class Test:
    def __init__(self, value: Annotated[int, Property(name="foo", defaultValue="10")]):
        self.value = value

    @Executable
    def get_value(self) -> int:
        return self.value
''')

        expect:
        getBean(context, "python.Test").get_value() == 10

        cleanup:
        context?.close()
    }

    void "test inject properties through constructor and method"() {
        given:
        def context = buildContext(propertyInjectionCode(), false, propertyValues())

        when:
        def constructorBean = getBean(context, "python.ConstructorPropertyInject")
        def methodBean = getBean(context, "python.MethodPropertyInject")

        then:
        constructorBean.get_string() == "foo"
        constructorBean.get_integer() == 10
        methodBean.get_string() == "foo"
        methodBean.get_integer() == 10

        cleanup:
        context?.close()
    }

    void "test inject property through field with explicit inject"() {
        given:
        def context = buildContext(propertyInjectionCode(), false, propertyValues())

        when:
        def bean = getBean(context, "python.FieldPropertyInject")

        then:
        bean.get_string() == "foo"
        bean.get_default_value() == 10

        cleanup:
        context?.close()
    }

    void "test inject property through field without explicit inject"() {
        given:
        def context = buildContext(propertyInjectionCode(), false, propertyValues())

        when:
        def bean = getBean(context, "python.FieldPropertyInject")

        then:
        bean.get_integer() == 10

        cleanup:
        context?.close()
    }

    void "test a class with only a property annotation is a bean and injected"() {
        given:
        def context = buildContext('''
from typing import Annotated
from micronaut.context.annotation import Executable, Property

class PropertyOnlyInject:
    integer: Annotated[int, Property(name="my.int")] = 0

    @Executable
    def get_integer(self) -> int:
        return self.integer
''', false, ["my.int": 10])

        expect:
        getBean(context, "python.PropertyOnlyInject").get_integer() == 10

        cleanup:
        context?.close()
    }

    private static String propertyInjectionCode() {
        '''
from typing import Annotated
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Executable, Property

@Singleton
class ConstructorPropertyInject:
    def __init__(
        self,
        string: Annotated[str, Property(name="my.string")],
        integer: Annotated[int, Property(name="my.int")]
    ):
        self.string = string
        self.integer = integer

    @Executable
    def get_string(self) -> str:
        return self.string

    @Executable
    def get_integer(self) -> int:
        return self.integer

@Singleton
class MethodPropertyInject:
    def __init__(self):
        self.string = None
        self.integer = 0

    @Inject
    def set_string(self, string: Annotated[str, Property(name="my.string")]):
        self.string = string

    @Inject
    def set_integer(self, integer: Annotated[int, Property(name="my.int")]):
        self.integer = integer

    @Executable
    def get_string(self) -> str:
        return self.string

    @Executable
    def get_integer(self) -> int:
        return self.integer

@Singleton
class FieldPropertyInject:
    string: Annotated[str, Inject, Property(name="my.string")] = None
    default_value: Annotated[int, Inject, Property(name="foo", defaultValue="10")] = 0
    integer: Annotated[int, Property(name="my.int")] = 0

    @Executable
    def get_string(self) -> str:
        return self.string

    @Executable
    def get_default_value(self) -> int:
        return self.default_value

    @Executable
    def get_integer(self) -> int:
        return self.integer
'''
    }

    private static Map<String, Object> propertyValues() {
        [
            "my.string": "foo",
            "my.int": "10"
        ]
    }
}
