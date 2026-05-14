package io.micronaut.python.annotation.processing.test

class BeanRegistrationSpec extends AbstractPythonTypeElementSpec {

    void "test inject bean registrations through constructor and method"() {
        given:
        def context = buildContext(beanRegistrationCode(false))

        when:
        def bean = getBean(context, "python.Test")

        then:
        bean.primary_name() == "one"
        bean.secondary_name() == "two"
        bean.constructor_count() == 2
        bean.method_count() == 2
        bean.constructor_names() == "one,two"
        bean.method_names() == "one,two"

        cleanup:
        context?.close()
    }

    void "test inject bean registrations through field"() {
        given:
        def context = buildContext(beanRegistrationCode(true))

        when:
        def bean = getBean(context, "python.Test")

        then:
        bean.field_count() == 2
        bean.field_names() == "one,two"

        cleanup:
        context?.close()
    }

    private static String beanRegistrationCode(boolean includeFieldInjection) {
        """
from typing import Annotated, List
from jakarta.inject import Inject, Named, Singleton
from micronaut.context.annotation import Executable, Primary

import java

BeanRegistration = java.type("io.micronaut.context.BeanRegistration")

class Foo:
    @Executable
    def name(self) -> str:
        return "base"

@Singleton
@Primary
class FooOne(Foo):
    @Executable
    def name(self) -> str:
        return "one"

@Singleton
@Named("two")
class FooTwo(Foo):
    @Executable
    def name(self) -> str:
        return "two"

@Singleton
class Test:
${includeFieldInjection ? '    field_registrations: Annotated[List[BeanRegistration[Foo]], Inject] = None\n' : ''}
    def __init__(
        self,
        registrations: List[BeanRegistration[Foo]],
        primary_bean: BeanRegistration[Foo],
        secondary_bean: Annotated[BeanRegistration[Foo], Named("two")]
    ):
        self.registrations = registrations
        self.primary_bean = primary_bean
        self.secondary_bean = secondary_bean
        self.method_registrations = None

    @Inject
    def set_regs(self, registrations: List[BeanRegistration[Foo]]):
        self.method_registrations = registrations

    @Executable
    def primary_name(self) -> str:
        return self.primary_bean.getBean().name()

    @Executable
    def secondary_name(self) -> str:
        return self.secondary_bean.getBean().name()

    @Executable
    def constructor_count(self) -> int:
        return len(self.registrations)

${includeFieldInjection ? '''    @Executable
    def field_count(self) -> int:
        return len(self.field_registrations)

''' : ''}
    @Executable
    def method_count(self) -> int:
        return len(self.method_registrations)

    @Executable
    def constructor_names(self) -> str:
        return ",".join(sorted([registration.getBean().name() for registration in self.registrations]))

${includeFieldInjection ? '''    @Executable
    def field_names(self) -> str:
        return ",".join(sorted([registration.getBean().name() for registration in self.field_registrations]))

''' : ''}
    @Executable
    def method_names(self) -> str:
        return ",".join(sorted([registration.getBean().name() for registration in self.method_registrations]))
"""
    }
}
