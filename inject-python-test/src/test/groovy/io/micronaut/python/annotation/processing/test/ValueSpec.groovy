package io.micronaut.python.annotation.processing.test

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value

class ValueSpec extends AbstractPythonTypeElementSpec {

    void "test value annotation metadata on field injection"() {
        given:
        def definition = buildBeanDefinition("python", "A", '''
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Value

@Singleton
class A:
    port: Annotated[int, Value("${foo.bar}")] = 0
''')

        expect:
        definition.injectedMethods.size() == 1
        definition.injectedMethods[0].arguments[0].annotationMetadata.stringValue(Value).get() == '${foo.bar}'
    }

    void "test configuration injection with value on factory method"() {
        given:
        def context = buildContext('''
from typing import Annotated
from micronaut.context.annotation import Bean, Factory, Value

class A:
    def __init__(self, port: int):
        self.port = port

class B:
    def __init__(self, a: A, port: int):
        self.a = a
        self.port = port

@Factory
class MyFactory:
    @Bean
    def new_a(self, port: Annotated[int, Value("${foo.bar}")]) -> A:
        return A(port)

    @Bean
    def new_b(self, a: A, port: Annotated[int, Value("${foo.bar}")]) -> B:
        return B(a, port)
''', false, ["foo.bar": "8080"])

        when:
        def factoryDefinition = getBeanDefinition(context, "python.MyFactory")
        def a = getBean(context, "python.A")
        def b = getBean(context, "python.B")

        then:
        factoryDefinition.hasAnnotation(Factory)
        a.asPolyglotValue().getMember("port").asInt() == 8080
        b.asPolyglotValue().getMember("a") != null
        b.asPolyglotValue().getMember("port").asInt() == 8080

        cleanup:
        context?.close()
    }

    void "test value expression resolver service is loaded from python context classloader"() {
        given:
        def context = buildContext('''
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Value

@Singleton
class A:
    def __init__(self, value: Annotated[str, Value("${python.service.loaded}")]):
        self.value = value
''')

        when:
        def bean = getBean(context, "python.A")

        then:
        bean.asPolyglotValue().getMember("value").asString() == "loaded"

        cleanup:
        context?.close()
    }
}
