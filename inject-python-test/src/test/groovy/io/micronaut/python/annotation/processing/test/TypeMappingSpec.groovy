/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.annotation.processing.test

import io.micronaut.aop.Interceptor
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.annotation.Factory
import io.micronaut.inject.BeanDefinition
import io.micronaut.python.annotation.processing.test.typemapping.AnimalSinkInterceptor
import io.micronaut.python.annotation.processing.test.typemapping.KeyedClient
import io.micronaut.python.annotation.processing.test.typemapping.KeyedRegistry
import io.micronaut.python.annotation.processing.test.typemapping.Shade
import io.micronaut.python.annotation.processing.test.typemapping.codec.Codec
import io.micronaut.python.annotation.processing.test.typemapping.codec.StringCodec
import io.micronaut.python.compiler.PyronautCompiler

/**
 * Mapping of Python types to the Java signatures of the generated stubs: maps with non-string keys, nullable
 * bytes, subclass instances passed as their base type, missing imports, nested enum annotation members and
 * star-imported generic types.
 */
class TypeMappingSpec extends AbstractPythonTypeElementSpec {

    void "dict with int, enum and Python class keys round-trips through Java"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from enum import Enum

from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected
from io.micronaut.python.annotation.processing.test.typemapping import KeyedRegistry, Shade


class Feature:
    def __init__(self, name: str):
        self.name = name

    def __hash__(self) -> int:
        return hash(self.name)

    def __eq__(self, other) -> bool:
        return isinstance(other, Feature) and other.name == self.name


class Color(Enum):
    RED = "red"
    BLUE = "blue"


@Introspected
@dataclass
class Point:
    x: int
    y: int


@Introspected
@dataclass
class Location:
    features: dict[Feature, Point]
    colors: dict[Color, Point]
    ids: dict[int, str]


@Singleton
class LocationRegistry(KeyedRegistry):

    def numbered(self, entries: dict[int, str]) -> dict[int, str]:
        return {key + 1: value.upper() for key, value in entries.items()}

    def shaded(self, entries: dict[Shade, str]) -> dict[Shade, str]:
        return {Shade.DARK if key == Shade.LIGHT else Shade.LIGHT: value for key, value in entries.items()}

    @Executable
    def locate(self, features: dict[Feature, Point]) -> dict[Feature, Point]:
        return {Feature(feature.name + "!"): Point(point.x + 1, point.y + 1) for feature, point in features.items()}

    @Executable
    def colored(self, colors: dict[Color, Point]) -> dict[Color, Point]:
        return {Color.BLUE if color == Color.RED else Color.RED: point for color, point in colors.items()}

    @Executable
    def describe(self, location: Location) -> str:
        features = ", ".join(f"{feature.name}={point.x}/{point.y}" for feature, point in sorted(location.features.items(), key=lambda item: item[0].name))
        colors = ", ".join(f"{color.value}={point.x}" for color, point in location.colors.items())
        ids = ", ".join(f"{key}={value}" for key, value in sorted(location.ids.items()))
        return f"{features}; {colors}; {ids}"
'''

        when:
        def context = buildContext(pythonCode)
        KeyedRegistry registry = getBean(context, "python.LocationRegistry") as KeyedRegistry
        def feature = context.classLoader.loadClass("python.Feature")
        def point = context.classLoader.loadClass("python.Point")
        def color = context.classLoader.loadClass("python.Color")
        def location = context.classLoader.loadClass("python.Location")

        then: 'a Java map with int keys'
        registry.numbered([1: "a", 2: "b"]) == [2: "A", 3: "B"]

        and: 'a Java map with Java enum keys'
        registry.shaded([(Shade.LIGHT): "light"]) == [(Shade.DARK): "light"]

        when: 'a Java map with Python class keys and values'
        def tree = feature.newInstance("tree")
        def origin = point.newInstance(1, 2)
        Map located = registry.locate([(tree): origin])

        then:
        located.size() == 1
        located.keySet()[0].class == feature
        located.keySet()[0].asPolyglotValue().getMember("name").asString() == "tree!"
        located.values()[0].class == point
        located.values()[0].x == 2
        located.values()[0].y == 3

        when: 'a Java map with Python enum keys'
        def red = color.getMethod("valueOf", String).invoke(null, "RED")
        def blue = color.getMethod("valueOf", String).invoke(null, "BLUE")
        Map colored = registry.colored([(red): origin])

        then:
        colored.size() == 1
        colored.keySet()[0] == blue
        colored.values()[0].x == 1

        when: 'a dataclass with map attributes built from Java'
        def built = location.newInstance([(tree): origin], [(blue): point.newInstance(5, 6)], [7: "seven"])

        then:
        registry.describe(built) == "tree=1/2; blue=5; 7=seven"
        built.features.keySet()[0].asPolyglotValue().getMember("name").asString() == "tree"
        built.features.values()[0].y == 2
        built.colors.keySet()[0] == blue
        built.ids == [7: "seven"]

        cleanup:
        context?.close()
    }

    void "a nullable bytes return type is a byte array"() {
        given:
        def pythonCode = '''
from typing import Optional

from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


@Singleton
class Payloads:

    @Executable
    def payload(self, present: bool) -> bytes | None:
        return b"abc" if present else None

    @Executable
    def optional_payload(self, present: bool) -> Optional[bytes]:
        return b"abc" if present else None

    @Executable
    def echo(self, data: bytes | None) -> bytes | None:
        return data
'''
        def tempDir = File.createTempDir("python-nullable-bytes", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()
        def generated = new File(tempDir, "python/Payloads.java").text

        then:
        generated.contains("byte[] payload(")
        generated.contains("Optional<byte[]> optional_payload(")
        generated.contains("byte[] echo(")
        !generated.contains("Byte ")
        !generated.contains("asByte()")

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.Payloads")
        BeanDefinition<?> definition = getBeanDefinition(context, "python.Payloads")

        then:
        definition.findMethod("payload", boolean).get().returnType.type == byte[]
        bean.payload(true) == "abc".bytes
        bean.payload(false) == null
        bean.optional_payload(true).get() == "abc".bytes
        bean.optional_payload(false).empty
        bean.echo("xyz".bytes) == "xyz".bytes
        bean.echo(null) == null

        cleanup:
        tempDir?.deleteDir()
        context?.close()
    }

    void "a Python subclass passed as its Python base type keeps its runtime type"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from dataclasses import dataclass

from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected
from io.micronaut.python.annotation.processing.test.typemapping import AnimalSink


class Animal:
    """The base type; only its subclasses are introspected."""
    name: str


@Introspected
@dataclass
class Cat(Animal):
    name: str
    lives: int


@Introspected
@dataclass
class Snake(Animal):
    name: str
    length: int


@AnimalSink
@Singleton
class AnimalClient(ABC):

    @abstractmethod
    def send(self, animal: Animal) -> None:
        ...

    def send_animal(self, animal: Animal) -> None:
        self.send(animal)


@Singleton
class Shelter:

    @Executable
    def adopt(self, name: str) -> Animal:
        return Cat(name, 9)

    @Executable
    def adopt_all(self) -> list[Animal]:
        return [Cat("Tom", 9), Snake("Kaa", 3)]
'''

        when:
        def context = buildContext(pythonCode)
        def interceptor = context.getBean(AnimalSinkInterceptor)
        def client = getBean(context, "python.AnimalClient")
        def cat = context.classLoader.loadClass("python.Cat").newInstance("Tom", 9)
        def snake = context.classLoader.loadClass("python.Snake").newInstance("Kaa", 3)
        client.send_animal(cat)
        client.send_animal(snake)

        then: 'the introduction receives the subclass stubs, which the serializer can introspect'
        interceptor.received.size() == 2
        interceptor.received[0].class.name == "python.Cat"
        interceptor.received[1].class.name == "python.Snake"
        interceptor.introspectionOf(0).beanType.name == "python.Cat"
        interceptor.introspectionOf(1).beanType.name == "python.Snake"
        interceptor.received[0].lives == 9

        when: 'the Python side passes a subclass instance it created itself'
        client.asPolyglotValue().invokeMember("send_animal", getBean(context, "python.Shelter").asPolyglotValue().invokeMember("adopt", "Felix"))

        then:
        interceptor.received[2].class.name == "python.Cat"
        interceptor.received[2].name == "Felix"

        when: 'a method declared to return the base type returns a subclass'
        def shelter = getBean(context, "python.Shelter")
        def adopted = shelter.adopt("Garfield")
        List all = shelter.adopt_all()

        then:
        adopted.class.name == "python.Cat"
        adopted.lives == 9
        all*.class*.name == ["python.Cat", "python.Snake"]

        cleanup:
        context?.close()
    }

    void "an import of a name that is neither a Python source nor a classpath class is a compile error"() {
        when:
        buildBeanDefinition("python", "Missing", '''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test.typemapping import Shade, NoSuchProvider


@Singleton
class Missing:
    def __init__(self, provider: NoSuchProvider | None):
        self.provider = provider
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("NoSuchProvider")
        e.message.contains("io.micronaut.python.annotation.processing.test.typemapping")
    }

    void "an import from a Java namespace whose package is absent from the classpath is a compile error"() {
        when:
        buildBeanDefinition("python", "Absent", '''
from jakarta.inject import Singleton
from micronaut.absent.ua import UserAgentProvider


@Singleton
class Absent:
    def __init__(self, provider: UserAgentProvider | None):
        self.provider = provider
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("UserAgentProvider")
        e.message.contains("io.micronaut.absent.ua")
    }

    void "imports of standard library and project Python modules are not Java imports"() {
        given:
        def tempDir = File.createTempDir("python-import-modules", "")
        new File(tempDir, "models").mkdirs()
        new File(tempDir, "models/Pet.py").text = '''
from dataclasses import dataclass


@dataclass
class Pet:
    name: str
'''
        new File(tempDir, "models/helpers.py").text = '''
def describe(name: str) -> str:
    return f"pet {name}"
'''
        new File(tempDir, "PetService.py").text = '''
from collections import OrderedDict
from typing import Optional

from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

from models.Pet import Pet
from models.helpers import describe
from models import Pet as PetModule


@Singleton
class PetService:

    @Executable
    def describe(self, name: str) -> str:
        return describe(Pet(name).name)
'''
        def compiler = PyronautCompiler.builder()
            .pythonSrc(tempDir.absolutePath)
            .build()

        when:
        def classLoader = compiler.buildClassLoader()

        then:
        classLoader.loadClass('python.$PetService$Definition')
        classLoader.loadClass('models.Pet')

        cleanup:
        tempDir?.deleteDir()
    }

    void "a nested enum constant or nested class constant as an annotation member is recorded as its value"() {
        given:
        def pythonCode = '''
import java

from io.micronaut.python.annotation.processing.test.typemapping import KeyedClient, Shade
from jakarta.inject import Singleton

AliasedClient = java.type("io.micronaut.python.annotation.processing.test.typemapping.KeyedClient")
Acknowledge = java.type("io.micronaut.python.annotation.processing.test.typemapping.KeyedClient$Acknowledge")


@Singleton
@KeyedClient(id="imported", acks=KeyedClient.Acknowledge.ALL, shade=Shade.DARK, retries=KeyedClient.Retries.ALL)
class ImportedClient:
    pass


@Singleton
@AliasedClient(id="aliased", acks=AliasedClient.Acknowledge.ONE, retries=AliasedClient.Retries.NONE)
class AliasedClientBean:
    pass


@Singleton
@KeyedClient(id="nested-alias", acks=Acknowledge.ALL)
class NestedAliasClient:
    pass
'''

        when:
        def context = buildContext(pythonCode)
        def imported = getBeanDefinition(context, "python.ImportedClient")
        def aliased = getBeanDefinition(context, "python.AliasedClientBean")
        def nestedAlias = getBeanDefinition(context, "python.NestedAliasClient")

        then:
        imported.stringValue(KeyedClient, "id").get() == "imported"
        imported.enumValue(KeyedClient, "acks", KeyedClient.Acknowledge).get() == KeyedClient.Acknowledge.ALL
        imported.enumValue(KeyedClient, "shade", Shade).get() == Shade.DARK
        imported.getAnnotation(KeyedClient).getRequiredValue("acks", String) == "ALL"
        aliased.enumValue(KeyedClient, "acks", KeyedClient.Acknowledge).get() == KeyedClient.Acknowledge.ONE
        aliased.getAnnotation(KeyedClient).getRequiredValue("acks", String) == "ONE"
        nestedAlias.enumValue(KeyedClient, "acks", KeyedClient.Acknowledge).get() == KeyedClient.Acknowledge.ALL
        nestedAlias.getAnnotation(KeyedClient).getRequiredValue("acks", String) == "ALL"

        and: 'a constant of a nested class of the annotation is recorded as its value'
        imported.intValue(KeyedClient, "retries").getAsInt() == -1
        imported.getAnnotation(KeyedClient).getRequiredValue("retries", Object) == -1
        aliased.intValue(KeyedClient, "retries").getAsInt() == 0

        cleanup:
        context?.close()
    }

    void "a star-imported generic type resolves like an explicitly imported one in a factory return type"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory
from io.micronaut.python.annotation.processing.test.typemapping.codec import *


@Factory
class CodecFactory:

    @Singleton
    def string_codec(self) -> Codec[str, int]:
        return StringCodec.INSTANCE

    @Singleton
    def named(self, codec: Codec[str, int]) -> StringCodec:
        return codec
'''

        when:
        def context = buildContext(pythonCode)
        def factory = getBeanDefinition(context, "python.CodecFactory")
        def codec = context.getBean(Codec)

        then:
        factory.hasAnnotation(Factory)
        codec.is(StringCodec.INSTANCE)
        context.getBean(StringCodec).is(StringCodec.INSTANCE)
        context.getBeanDefinition(Codec).getTypeArguments(Codec)*.type == [String, Integer]

        cleanup:
        context?.close()
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.beanDefinitions(
            RuntimeBeanDefinition.builder(new AnimalSinkInterceptor())
                .singleton(true)
                .exposedTypes(AnimalSinkInterceptor.class, Interceptor.class)
                .build()
        )
    }
}
