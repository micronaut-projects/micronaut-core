package io.micronaut.python.annotation.processing.test

import io.micronaut.python.annotation.processing.test.defaults.Describable
import io.micronaut.python.annotation.processing.test.defaults.DescribableCaller

import java.lang.reflect.Method

/**
 * A Python class implementing a Java interface has the interface's default methods on the Python side as well:
 * when the bean is injected into Python code or looked up from the context in Python, the default methods that
 * the class does not override delegate to the Java implementation, and a Python override of a default method is
 * what both the Python and the Java view of the bean run.
 */
class InterfaceDefaultMethodSpec extends AbstractPythonTypeElementSpec {

    void "test default methods of the implemented Java interface are available on a python bean injected into python"() {
        given:
        def pythonCode = '''
from typing import Annotated
from jakarta.inject import Inject, Singleton
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Executable
import java

Describable = java.type("io.micronaut.python.annotation.processing.test.defaults.Describable")


@Singleton
class PythonDescribable(Describable):

    def name(self) -> str:
        return "python"


class PlainDescribable(Describable):

    def __init__(self, name: str):
        self._name = name

    def name(self) -> str:
        return self._name


@Singleton
class Consumer:

    def __init__(self, target: PythonDescribable, context: ApplicationContext):
        self.target = target
        self.context = context

    @Executable
    def describe_target(self) -> str:
        return self.target.describe()

    @Executable
    def describe_target_with(self) -> str:
        return self.target.describeWith("x", 2)

    @Executable
    def describe_target_all(self) -> list[str]:
        return list(self.target.describeAll(["-a", "-b"]))

    @Executable
    def target_order(self) -> int:
        return self.target.getOrder()

    @Executable
    def target_is_python_object(self) -> bool:
        return isinstance(self.target, PythonDescribable) and self.target == self.target

    @Executable
    def target_self_is_target(self) -> bool:
        return self.target.self() is self.target

    @Executable
    def describe_context_bean(self) -> str:
        return self.context.getBean(PythonDescribable).describe()

    @Executable
    def describe_new_instance(self) -> str:
        return PythonDescribable().describeWith("n", 1)

    @Executable
    def describe_plain_instance(self) -> str:
        return PlainDescribable("plain").describe()
'''

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.PythonDescribable")
        def consumer = getBean(context, "python.Consumer")
        Class<?> stub = bean.getClass()

        then: "the default methods are not bridged, the Java view runs the Java default implementation"
        bean instanceof Describable
        stub.declaredMethods.every { Method m -> !(m.name in ["describe", "describeWith", "describeAll", "getOrder"]) }
        DescribableCaller.describe(bean) == "d:python"
        DescribableCaller.describeWith(bean, "x", 2) == "xxpython"
        DescribableCaller.order(bean) == 0

        and: "the Python object injected into another Python bean has the default methods"
        consumer.describe_target() == "d:python"
        consumer.describe_target_with() == "xxpython"
        consumer.describe_target_all() == ["python-a", "python-b"]
        consumer.target_order() == 0

        and: "the injected object keeps its Python identity"
        consumer.target_is_python_object()
        consumer.target_self_is_target()

        and: "the default methods are available on a bean looked up in Python and on an instance created in Python"
        consumer.describe_context_bean() == "d:python"
        consumer.describe_new_instance() == "npython"
        consumer.describe_plain_instance() == "d:plain"

        cleanup:
        context?.close()
    }

    void "test default methods are inherited by python subclasses and not installed over python definitions"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
import java

Describable = java.type("io.micronaut.python.annotation.processing.test.defaults.Describable")


class Base(Describable):

    def name(self) -> str:
        return "base"

    def describeWith(self, prefix: str, times: int) -> str:
        return "base-with:" + prefix + str(times)


@Singleton
class Sub(Base, Describable):

    def name(self) -> str:
        return "sub"


@Singleton
class Consumer:

    def __init__(self, target: Sub):
        self.target = target

    @Executable
    def describe_target(self) -> str:
        return self.target.describe()

    @Executable
    def describe_target_with(self) -> str:
        return self.target.describeWith("x", 2)
'''

        when:
        def context = buildContext(pythonCode)
        def consumer = getBean(context, "python.Consumer")

        then: "the subclass inherits the delegating method and the Python definition of the base class is kept"
        consumer.describe_target() == "d:sub"
        consumer.describe_target_with() == "base-with:x2"

        cleanup:
        context?.close()
    }

    void "test default methods of imported and generic Java interfaces are available in python"() {
        given:
        def pythonCode = '''
from typing import Annotated
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.defaults import Describable, Holder


@Singleton
class PythonDescribable(Describable):

    def name(self) -> str:
        return "imported"


@Singleton
class NoneHolder(Holder[str]):

    def value(self) -> str | None:
        return None


@Singleton
class Consumer:
    describable: Annotated[PythonDescribable, Inject]
    holder: Annotated[NoneHolder, Inject]

    @Executable
    def describe_target(self) -> str:
        return self.describable.describe()

    @Executable
    def holder_value_or(self) -> str:
        return self.holder.valueOr("fallback")

    @Executable
    def holder_description(self) -> str:
        return self.holder.describeValue()
'''

        when:
        def context = buildContext(pythonCode)
        def consumer = getBean(context, "python.Consumer")

        then:
        consumer.describe_target() == "d:imported"
        consumer.holder_value_or() == "fallback"
        consumer.holder_description() == "holder:null"

        cleanup:
        context?.close()
    }

    void "test python override of a default method is what the Java and the Python view run"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
import java

Describable = java.type("io.micronaut.python.annotation.processing.test.defaults.Describable")


@Singleton
class PythonDescribable(Describable):

    def name(self) -> str:
        return "python"

    def describe(self) -> str:
        return "py:" + self.name()

    def describeWith(self, prefix: str, times: int) -> str:
        return "py:" + prefix + str(times)

    def getOrder(self) -> int:
        return 7


@Singleton
class Consumer:

    def __init__(self, target: PythonDescribable):
        self.target = target

    @Executable
    def describe_target(self) -> str:
        return self.target.describe()

    @Executable
    def describe_target_with(self) -> str:
        return self.target.describeWith("x", 2)
'''

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.PythonDescribable")
        def consumer = getBean(context, "python.Consumer")
        Class<?> stub = bean.getClass()

        then: "the overrides are bridged with the inherited signature"
        stub.getDeclaredMethod("describe").returnType == String
        stub.getDeclaredMethod("describeWith", String, int).returnType == String
        stub.getDeclaredMethod("getOrder").returnType == int

        and: "a Java caller reaches the Python overrides through the interface"
        DescribableCaller.describe(bean) == "py:python"
        DescribableCaller.describeWith(bean, "x", 2) == "py:x2"
        DescribableCaller.order(bean) == 7

        and: "so does Python"
        consumer.describe_target() == "py:python"
        consumer.describe_target_with() == "py:x2"

        cleanup:
        context?.close()
    }

    void "test default methods are available on local classes defined inside a method and inside a function"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.defaults import Describable, DescribableCaller


def make_local():
    class FunctionLocal(Describable):

        def name(self) -> str:
            return "fnlocal"

    return FunctionLocal()


@Singleton
class Probe:

    @Executable
    def describe_method_local(self) -> str:
        class Local(Describable):

            def name(self) -> str:
                return "local"

        return Local().describeWith("m", 1) + "/" + DescribableCaller.describe(Local())

    @Executable
    def describe_function_local(self) -> str:
        return make_local().describe() + "/" + DescribableCaller.describe(make_local())

    @Executable
    def function_local_order(self) -> int:
        return make_local().getOrder()

    @Executable
    def function_local_all(self) -> list[str]:
        return list(make_local().describeAll(["-a"]))

    @Executable
    def function_local_self(self) -> bool:
        local = make_local()
        return local.self() is local
'''

        when:
        def context = buildContext(pythonCode)
        def probe = getBean(context, "python.Probe")

        then: "a class defined inside a method of a bean (a class body) has the default methods"
        probe.describe_method_local() == "mlocal/d:local"

        and: "so does a class defined inside a module-level function, which has no generated stub"
        probe.describe_function_local() == "d:fnlocal/d:fnlocal"
        probe.function_local_order() == 0
        probe.function_local_all() == ["fnlocal-a"]
        probe.function_local_self()

        cleanup:
        context?.close()
    }
}
