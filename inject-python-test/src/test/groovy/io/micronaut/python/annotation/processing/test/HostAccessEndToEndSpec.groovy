package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.GraalPyRuntimeUtil
import io.micronaut.context.python.ValueCoercible
import io.micronaut.core.io.Writable
import io.micronaut.json.JsonMapper
import org.graalvm.polyglot.Context
import spock.lang.Stepwise

import java.nio.charset.StandardCharsets

@Stepwise
class HostAccessEndToEndSpec extends AbstractPythonTypeElementSpec {

    void "HostAccess registers generated TargetTypeMapping allowing Value.as on stub"() {
        given:
        String py = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected


@dataclass
@Introspected
class Author:
    name: str

@dataclass
@Introspected
class Book:
    title: str
    pages: int
    authors: list[Author]


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        def value = polyglot.eval("python", "Book('The Guide', 321, [Author('A'), Author('B')])")
        Class<?> bookClass = ctx.classLoader.loadClass('python.Book')
        Class<?> authorClass = ctx.classLoader.loadClass('python.Author')
        def converted = value.as(bookClass)

        then:
        converted != null
        converted instanceof ValueCoercible
        converted.title == "The Guide"
        converted.pages == 321
        converted.authors.every { authorClass.isInstance(it)}
        converted.authors*.name == ['A', 'B']
        ((ValueCoercible) converted).asPolyglotValue().getMember('title').asString() == 'The Guide'

        cleanup:
        ctx?.close()
    }

    void "HostAccess exposes non-introspected dataclass properties on generated stub"() {
        given:
        String py = '''
from dataclasses import dataclass


@dataclass
class Metadata:
    version: float
    deployment_id: int


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        def value = polyglot.eval("python", "Metadata(3.6, 42)")
        Class<?> metadataClass = ctx.classLoader.loadClass('python.Metadata')
        def converted = value.as(metadataClass)
        polyglot.getBindings("python").putMember("metadata", converted)

        then:
        converted != null
        converted instanceof ValueCoercible
        converted.version == 3.6d
        converted.deployment_id == 42
        polyglot.eval("python", "metadata.version").asDouble() == 3.6d
        polyglot.eval("python", "metadata.deployment_id").asInt() == 42
        ((ValueCoercible) converted).asPolyglotValue().getMember('version').asDouble() == 3.6d

        cleanup:
        ctx?.close()
    }

    void "HostAccess keeps stateful Python objects with lifecycle methods on stored value path"() {
        given:
        String py = '''
from jakarta.annotation import PreDestroy


class Connection:
    stopped: bool = False

    @PreDestroy
    def stop(self):
        self.stopped = True


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        def value = polyglot.eval("python", "Connection()")
        Class<?> connectionClass = ctx.classLoader.loadClass('python.Connection')
        def converted = value.as(connectionClass)
        def original = ((ValueCoercible) converted).asPolyglotValue()
        polyglot.getBindings("python").putMember("connection", converted)
        polyglot.eval("python", "connection.stop()")

        then:
        original.getMember('stopped').asBoolean()

        cleanup:
        ctx?.close()
    }

    void "HostAccess unwraps generated proxy stubs when Java accepts Object"() {
        given:
        String py = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Person:
    name: str
    age: int = 0


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        def unwrapped = polyglot.eval("python", '''
import java
ObjectReceiver = java.type("io.micronaut.python.annotation.processing.test.HostAccessEndToEndSpec$ObjectReceiver")
Person = java.type("python.Person")

person = Person("Fred")
ObjectReceiver.isValueCoercible(person)
''')

        then:
        unwrapped.asBoolean()

        cleanup:
        ctx?.close()
    }

    void "HostAccess converts generated Python class objects to Java Class"() {
        given:
        String py = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Person:
    name: str


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        def className = polyglot.eval("python", '''
import java
ClassReceiver = java.type("io.micronaut.python.annotation.processing.test.HostAccessEndToEndSpec$ClassReceiver")

ClassReceiver.className(Person)
''')

        then:
        className.asString() == 'python.Person'

        cleanup:
        ctx?.close()
    }

    void "HostAccess serializes generated proxy stubs as Python bean properties"() {
        given:
        String py = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Message:
    text: str


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        Class<?> messageClass = ctx.classLoader.loadClass('python.Message')
        def message = polyglot.eval("python", "Message('Hello')").as(messageClass)
        String json = ctx.getBean(JsonMapper).writeValueAsString(message)

        then:
        json == '{"text":"Hello"}'

        cleanup:
        ctx?.close()
    }

    void "bare Micronaut annotation decorators preserve decorated Python dataclasses at runtime"() {
        given:
        String py = '''
from dataclasses import dataclass
from micronaut.python.compiler import Serdeable


@Serdeable
@dataclass
class Detail:
    name: str


@Serdeable
@dataclass
class Message:
    detail: Detail | None = None


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        Class<?> messageClass = ctx.classLoader.loadClass('python.Message')
        def message = polyglot.eval("python", "Message(Detail('Hello'))").as(messageClass)
        String json = ctx.getBean(JsonMapper).writeValueAsString(message)

        then:
        json == '{"detail":{"name":"Hello"}}'

        cleanup:
        ctx?.close()
    }

    void "Serdeable Python dataclasses serialize list properties"() {
        given:
        String py = '''
from dataclasses import dataclass
from micronaut.python.compiler import Serdeable


@Serdeable
@dataclass
class Period:
    temperature: int = 0


@Serdeable
@dataclass
class ForecastProperties:
    periods: list[Period] | None = None


@Serdeable
@dataclass
class Forecast:
    properties: ForecastProperties | None = None


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        Class<?> forecastClass = ctx.classLoader.loadClass('python.Forecast')
        def forecast = polyglot.eval("python", "Forecast(ForecastProperties(periods=[Period(68)]))").as(forecastClass)
        String json = ctx.getBean(JsonMapper).writeValueAsString(forecast)

        then:
        json == '{"properties":{"periods":[{"temperature":68}]}}'

        cleanup:
        ctx?.close()
    }

    void "runtime conversion returns generated wrapper for Python value assignable to Java interface"() {
        given:
        String py = '''
from micronaut.core.annotation import Introspected
from micronaut.core.io import Writable


@Introspected
class TemplateWritable(Writable):
    def __init__(self, text: str):
        self.text = text

    def writeTo(self, writer):
        writer.write(self.text)


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        def value = polyglot.eval("python", "TemplateWritable('generated')")
        Writable writable = GraalPyRuntimeUtil.convertValue(value, Writable.class)
        def out = new ByteArrayOutputStream()
        writable.writeTo(out, StandardCharsets.UTF_8)

        then:
        writable.getClass().getName() == 'python.TemplateWritable'
        out.toString(StandardCharsets.UTF_8) == 'generated'

        cleanup:
        ctx?.close()
    }

    static final class ObjectReceiver {
        static boolean isValueCoercible(Object value) {
            value instanceof ValueCoercible && value.getClass().getName() == 'python.Person'
        }
    }

    static final class ClassReceiver {
        static String className(Class<?> value) {
            value.getName()
        }
    }
}
