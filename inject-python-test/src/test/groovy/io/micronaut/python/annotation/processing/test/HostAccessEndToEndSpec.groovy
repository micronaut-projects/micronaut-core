package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.ContextHolder
import io.micronaut.context.python.GraalPyContextFactory
import io.micronaut.context.python.GraalPyRuntimeUtil
import io.micronaut.context.python.ValueCoercible
import io.micronaut.core.io.Writable
import io.micronaut.json.JsonMapper
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
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

    void "Python bean extending Java abstract class remains assignable to inherited interface"() {
        given:
        String py = '''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test import HeaderTokenReaderLike


@Singleton
class ApiKeyTokenReader(HeaderTokenReaderLike):
    def getPrefix(self) -> str | None:
        return None

    def getHeaderName(self) -> str:
        return "X-API-KEY"


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Class<?> generatedClass = ctx.classLoader.loadClass('python.ApiKeyTokenReader')
        def reader = ctx.getBean(TokenReaderLike)

        then:
        HeaderTokenReaderLike.isAssignableFrom(generatedClass)
        TokenReaderLike.isAssignableFrom(generatedClass)
        reader.findToken("XXX") == "XXX"

        cleanup:
        ctx?.close()
    }

    void "Python bean extending Java abstract class with constructor calls host super constructor"() {
        given:
        String py = '''
from jakarta.inject import Singleton
from micronaut.python.annotation.processing.test import ConstructorBackedHandler, HandlerDependency


@Singleton
class PythonHandlerDependency(HandlerDependency):
    def name(self) -> str:
        return "dependency"


@Singleton
class PythonConstructorBackedHandler(ConstructorBackedHandler):
    def __init__(self, dependency: HandlerDependency):
        super().__init__(dependency)

    def handle(self) -> str:
        return self.dependencyName()


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Class<?> generatedClass = ctx.classLoader.loadClass('python.PythonConstructorBackedHandler')
        def handler = ctx.getBean(ConstructorBackedHandler)

        then:
        ConstructorBackedHandler.isAssignableFrom(generatedClass)
        handler.dependencyName() == "dependency"
        handler.handle() == "dependency"

        cleanup:
        ctx?.close()
    }

    void "HostAccess converts nested properties dataclass list fields"() {
        given:
        String py = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected


@dataclass
@Introspected
class Period:
    temperature: int
    summary: str


@dataclass
@Introspected
class ForecastProperties:
    periods: list[Period] | None = None


@dataclass
@Introspected
class Forecast:
    properties: ForecastProperties | None = None


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        def value = polyglot.eval("python", "Forecast(ForecastProperties([Period(68, 'Clear')]))")
        Class<?> forecastClass = ctx.classLoader.loadClass('python.Forecast')
        Class<?> forecastPropertiesClass = ctx.classLoader.loadClass('python.ForecastProperties')
        Class<?> periodClass = ctx.classLoader.loadClass('python.Period')
        def converted = value.as(forecastClass)
        def convertedProperties = forecastClass.getField('properties').get(converted)
        def convertedPeriods = forecastPropertiesClass.getField('periods').get(convertedProperties)

        then:
        converted != null
        convertedProperties != null
        forecastPropertiesClass.isInstance(convertedProperties)
        convertedPeriods != null
        convertedPeriods.size() == 1
        periodClass.isInstance(convertedPeriods[0])
        periodClass.getField('temperature').get(convertedPeriods[0]) == 68
        periodClass.getField('summary').get(convertedPeriods[0]) == 'Clear'

        cleanup:
        ctx?.close()
    }

    void "generated wrapper conversion does not require HostAccess target mappings for nested list fields"() {
        given:
        String py = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected


@dataclass
@Introspected
class Period:
    temperature: int
    summary: str


@dataclass
@Introspected
class ForecastProperties:
    periods: list[Period] | None = None


@dataclass
@Introspected
class Forecast:
    properties: ForecastProperties | None = None


'''

        ApplicationContext ctx = buildContext(py, true)
        ClassLoader classLoader = ctx.classLoader
        ctx.close()
        ContextHolder.setReuseContext(false)
        ContextHolder.resetContext()

        when:
        Context polyglot = GraalPyContextFactory.bootstrapReusableContext(classLoader, Map.of(), "pyronaut_application.py")
        def value = polyglot.eval("python", "Forecast(ForecastProperties([Period(68, 'Clear')]))")
        Class<?> forecastClass = classLoader.loadClass('python.Forecast')
        Class<?> forecastPropertiesClass = classLoader.loadClass('python.ForecastProperties')
        Class<?> periodClass = classLoader.loadClass('python.Period')
        def converted = forecastClass.getMethod('fromPolyglotValue', org.graalvm.polyglot.Value).invoke(null, value)
        def convertedProperties = forecastClass.getField('properties').get(converted)
        def convertedPeriods = forecastPropertiesClass.getField('periods').get(convertedProperties)

        then:
        converted != null
        convertedProperties != null
        convertedPeriods != null
        convertedPeriods.size() == 1
        periodClass.isInstance(convertedPeriods[0])
        periodClass.getField('temperature').get(convertedPeriods[0]) == 68
        periodClass.getField('summary').get(convertedPeriods[0]) == 'Clear'

        cleanup:
        ContextHolder.setReuseContext(false)
        ContextHolder.resetContext()
    }

    void "reusable context loads generated target mappings for overloaded static Java methods"() {
        given:
        String py = '''
import java

from jakarta.inject import Singleton

TokenReaderLike = java.type("io.micronaut.python.annotation.processing.test.TokenReaderLike")


@Singleton
class PythonTokenReader(TokenReaderLike):
    def findToken(self, header: str) -> str:
        return "value-" + header


'''

        ApplicationContext ctx = buildContext(py, true)
        ClassLoader classLoader = ctx.classLoader
        ctx.close()
        ContextHolder.setReuseContext(false)
        ContextHolder.resetContext()

        when:
        Context polyglot = GraalPyContextFactory.bootstrapReusableContext(classLoader, Map.of(), "pyronaut_application.py")
        def result = polyglot.eval("python", '''
import java

Factory = java.type("io.micronaut.python.annotation.processing.test.HostAccessEndToEndSpec$StaticTokenReaderFactory")
PythonTokenReaderHost = java.type("python.PythonTokenReader")

Factory.create(PythonTokenReaderHost.fromPolyglotValue(PythonTokenReader()))
''')

        then:
        result.asString() == 'value-test'

        cleanup:
        ContextHolder.setReuseContext(false)
        ContextHolder.resetContext()
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

    void "Serdeable frozen dataclass wrapper reconstructs Python value from Java fields"() {
        given:
        String py = '''
from dataclasses import dataclass
from micronaut.python.compiler import Serdeable


@Serdeable
@dataclass(frozen=True)
class Book:
    isbn: str
    name: str


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        Class<?> bookClass = ctx.classLoader.loadClass('python.Book')
        def book = bookClass.getDeclaredConstructor(String, String)
            .newInstance('1491950358', 'Building Microservices')
        Value pythonBook = ((ValueCoercible) book).asPolyglotValue()
        polyglot.getBindings("python").putMember("book", book)

        then:
        pythonBook.getMember('isbn').asString() == '1491950358'
        pythonBook.getMember('name').asString() == 'Building Microservices'
        polyglot.eval("python", "book.isbn").asString() == '1491950358'
        polyglot.eval("python", "book.name").asString() == 'Building Microservices'

        cleanup:
        ctx?.close()
    }

    void "Serdeable frozen dataclass wrapper reconstructs Python enum fields from Java enum fields"() {
        given:
        String py = '''
from dataclasses import dataclass
from enum import Enum
from micronaut.python.compiler import Serdeable


class Player(Enum):
    WHITE = "w"
    BLACK = "b"


@Serdeable
@dataclass(frozen=True)
class Move:
    player: Player


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        Class<?> moveClass = ctx.classLoader.loadClass('python.Move')
        Class<?> playerClass = ctx.classLoader.loadClass('python.Player')
        def white = Enum.valueOf((Class<Enum>) playerClass, "WHITE")
        def move = moveClass.getDeclaredConstructor(playerClass).newInstance(white)
        Value pythonMove = ((ValueCoercible) move).asPolyglotValue()
        polyglot.getBindings("python").putMember("move", move)

        then:
        white.name() == "WHITE"
        pythonMove.getMember('player').getMember('value').asString() == 'w'

        cleanup:
        ctx?.close()
    }

    void "generated dataclass wrapper setters called from Python update Java fields"() {
        given:
        String py = '''
from dataclasses import dataclass
from enum import Enum
from micronaut.core.annotation import Introspected


class Player(Enum):
    WHITE = "w"
    BLACK = "b"


@Introspected
@dataclass
class Game:
    draw: bool = False
    winner: Player | None = None


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        Class<?> gameClass = ctx.classLoader.loadClass('python.Game')
        Class<?> playerClass = ctx.classLoader.loadClass('python.Player')
        def game = polyglot.eval("python", "Game()").as(gameClass)
        polyglot.getBindings("python").putMember("game", game)
        polyglot.eval("python", "game.setDraw(True)")
        polyglot.eval("python", "game.setWinner(Player.BLACK)")
        def black = Enum.valueOf((Class<Enum>) playerClass, "BLACK")

        then:
        game.getDraw()
        game.isDraw()
        game.getWinner() == black
        ((ValueCoercible) game).asPolyglotValue().getMember("draw").asBoolean()
        ((ValueCoercible) game).asPolyglotValue().getMember("winner").getMember("value").asString() == "b"
        polyglot.eval("python", "game.draw").asBoolean()
        polyglot.eval("python", "game.winner.value").asString() == "b"

        when:
        polyglot.eval("python", "game.draw = False")
        polyglot.eval("python", "game.winner = Player.WHITE")
        def white = Enum.valueOf((Class<Enum>) playerClass, "WHITE")

        then:
        !game.getDraw()
        !game.isDraw()
        game.getWinner() == white
        !((ValueCoercible) game).asPolyglotValue().getMember("draw").asBoolean()
        ((ValueCoercible) game).asPolyglotValue().getMember("winner").getMember("value").asString() == "w"

        cleanup:
        ctx?.close()
    }

    void "Python enum value converts to generated enum when passed to Java Object method"() {
        given:
        String py = '''
from enum import Enum


class Player(Enum):
    WHITE = "w"
    BLACK = "b"


'''

        ApplicationContext ctx = buildContext(py, true)

        when:
        Context polyglot = ctx.getBean(Context)
        Class<?> playerClass = ctx.classLoader.loadClass('python.Player')
        Value pythonBlack = polyglot.eval("python", "Player.BLACK")
        def javaBlack = pythonBlack.as(playerClass)
        polyglot.getBindings("python").putMember("json_mapper", ctx.getBean(JsonMapper))
        String json = polyglot.eval("python", "json_mapper.writeValueAsString(Player.BLACK)").asString()

        then:
        playerClass.isInstance(javaBlack)
        javaBlack.name() == "BLACK"
        json == '"BLACK"'

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

    static final class StaticTokenReaderFactory {
        static String create(TokenReaderLike reader) {
            reader.findToken("test")
        }

        static String create(HeaderTokenReaderLike reader) {
            reader.findToken("test")
        }
    }
}
