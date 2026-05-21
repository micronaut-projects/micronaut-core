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

    void "test each bean interceptor receives target qualifier"() {
        given:
        def pythonCode = '''\
from typing import Annotated
from jakarta.inject import Named, Singleton
from micronaut.aop import Around, InterceptorBean, MethodInvocationContext
from micronaut.context.annotation import EachBean, EachProperty, Executable, Prototype

import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
Qualifier = java.type("io.micronaut.context.Qualifier")

@Around
def Transactional(target):
    return target

@EachProperty(value="mydatasources", primary="default")
class MyDataSource:
    pass

@EachBean(MyDataSource.__qualname__)
@Transactional
class MyTransactionalConnection:
    @Executable
    def catalog(self) -> str:
        return "unintercepted"

@Prototype
@InterceptorBean(Transactional)
class MyInterceptor(MethodInterceptor):
    def __init__(self, qualifier: Qualifier):
        self.qualifier = qualifier

    def intercept(self, context: MethodInvocationContext):
        return str(self.qualifier)

@Singleton
class MyBean:
    def __init__(
        self,
        default_connection: Annotated[MyTransactionalConnection, Named("default")],
        foo_connection: Annotated[MyTransactionalConnection, Named("foo")],
        bar_connection: Annotated[MyTransactionalConnection, Named("bar")]
    ):
        self.default_connection = default_connection
        self.foo_connection = foo_connection
        self.bar_connection = bar_connection
'''

        expect:
        buildClassElement(pythonCode, "MyInterceptor") {
            it.primaryConstructor.get().parameters[0].type.name
        } == "io.micronaut.context.Qualifier"

        when:
        def context = buildContext(pythonCode, false, [
            "mydatasources.default.xyz": "111",
            "mydatasources.foo.xyz": "111",
            "mydatasources.bar.xyz": "111"
        ])
        def service = getBean(context, "python.MyBean")
        def serviceValue = service.asPolyglotValue()

        then:
        serviceValue.getMember("default_connection").invokeMember("catalog").asString() == "@Named('default')"
        serviceValue.getMember("foo_connection").invokeMember("catalog").asString() == "@Named('foo')"
        serviceValue.getMember("bar_connection").invokeMember("catalog").asString() == "@Named('bar')"

        cleanup:
        context?.close()
    }
}
