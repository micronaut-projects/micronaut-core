package io.micronaut.python.annotation.processing.test

import io.micronaut.aop.Intercepted
import io.micronaut.inject.qualifiers.Qualifiers

class NamedAopAdviceSpec extends AbstractPythonTypeElementSpec {

    void "test named refreshable factory beans resolve the correct target by qualifier"() {
        given:
        def context = buildContext('''\
from typing import Annotated
from micronaut.context.annotation import EachProperty, Executable, Factory, Parameter
from micronaut.runtime.context.scope import Refreshable

class NamedBean:
    name: str = ""

    @Executable
    def do_stuff(self) -> str:
        return self.name

@Factory
class NamedFactory:
    @EachProperty(value="aop.test.named", primary="default")
    @Refreshable
    def named_bean(self, name: Annotated[str, Parameter]) -> NamedBean:
        bean = NamedBean()
        bean.name = name
        return bean
''', false, [
            "aop.test.named.default": 0,
            "aop.test.named.one": 1,
            "aop.test.named.two": 2
        ])
        def namedBeanClass = context.classLoader.loadClass("python.NamedBean")

        expect:
        context.getBean(namedBeanClass) instanceof Intercepted
        context.getBean(namedBeanClass).do_stuff() == "default"
        context.getBean(namedBeanClass, Qualifiers.byName("one")).do_stuff() == "one"
        context.getBean(namedBeanClass, Qualifiers.byName("two")).do_stuff() == "two"
        context.getBeansOfType(namedBeanClass).size() == 3
        context.getBeansOfType(namedBeanClass).every { it instanceof Intercepted }

        cleanup:
        context?.close()
    }
}
