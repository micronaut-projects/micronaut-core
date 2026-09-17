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

import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.python.annotation.processing.test.constants.CustomSerde
import io.micronaut.python.annotation.processing.test.constants.CustomSerializer
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Named

/**
 * Decorator members can reference Java class constants, Python module or class constants and classes.
 */
class AnnotationConstantMemberSpec extends AbstractPythonTypeElementSpec {

    void "test a Java class constant as a qualifier member"() {
        given:
        def context = buildContext('''
from typing import Annotated

from jakarta.inject import Named, Singleton
from io.micronaut.python.annotation.processing.test.constants import MapperNames

@Singleton
@Named(MapperNames.PROPERTIES)
class PropertiesMapper:
    def name(self) -> str:
        return "properties"

@Singleton
@Named("json")
class JsonMapper:
    def name(self) -> str:
        return "json"

@Singleton
class MapperService:
    def __init__(self, mapper: Annotated[PropertiesMapper, Named(MapperNames.PROPERTIES)]):
        self.mapper = mapper

    def mapper_name(self) -> str:
        return self.mapper.name()
''')

        expect:
        getBeanDefinition(context, "python.PropertiesMapper").getAnnotationMetadata().stringValue(Named).get() == "properties"
        getBean(context, "python.PropertiesMapper", Qualifiers.byName("properties")).asPolyglotValue().invokeMember("name").asString() == "properties"
        getBean(context, "python.MapperService").asPolyglotValue().invokeMember("mapper_name").asString() == "properties"

        cleanup:
        context?.close()
    }

    void "test module and class constants as route members"() {
        given:
        def context = buildContext('''
from micronaut.http.annotation import Controller, Get, Post

BOOKS_PATH = "/books"
LIST_PATH = "/list"

@Controller(BOOKS_PATH)
class BookController:
    SAVE_PATH = "/save"

    @Get(LIST_PATH)
    def list(self) -> str:
        return "list"

    @Post(SAVE_PATH)
    def save(self) -> str:
        return "saved"
''', true)
        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL).toBlocking()

        expect:
        client.retrieve("/books/list") == "list"
        client.retrieve(io.micronaut.http.HttpRequest.POST("/books/save", "")) == "saved"

        cleanup:
        client.close()
        context?.close()
    }

    void "test a Java class constant as a route member and an int member"() {
        given:
        def context = buildContext('''
from micronaut.http.annotation import Controller, Get
from micronaut.core.annotation import Order
from io.micronaut.python.annotation.processing.test.constants import MapperNames

@Controller("/mappers")
@Order(MapperNames.MAX_RETRIES)
class MapperController:

    @Get(MapperNames.PROPERTIES)
    def properties(self) -> str:
        return "properties"
''', true)
        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL).toBlocking()

        expect:
        client.retrieve("/mappers/properties") == "properties"
        getBeanDefinition(context, "python.MapperController").intValue(io.micronaut.core.annotation.Order).getAsInt() == 3

        cleanup:
        client.close()
        context?.close()
    }

    void "test a class-valued member references a Python or Java class"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from typing import Annotated

from micronaut.core.annotation import Introspected
from io.micronaut.python.annotation.processing.test.constants import CustomSerde, CustomSerializer, MapperNames

class ReversePointSerde:
    def serialize(self, value: str) -> str:
        return value[::-1]

@Introspected
@dataclass
class Point:
    x: int
    y: int

@Introspected
@dataclass
class Place:
    point: Annotated[
        Point,
        CustomSerde.Serializable(using=ReversePointSerde),
        CustomSerde.Deserializable(using=ReversePointSerde),
    ]
    other: Annotated[Point, CustomSerializer(using=MapperNames, name="other")]
    custom: Annotated[Point, CustomSerializer(using=ReversePointSerde)]
    plain: Point
''')
        def introspection = getBeanIntrospection(context, "python.Place")

        expect: "nested annotations with a class-valued member on an attribute"
        introspection.getRequiredProperty("point", Object).getAnnotation(CustomSerde.Serializable).annotationClassValue("using").get().name == "python.ReversePointSerde"
        introspection.getRequiredProperty("point", Object).getAnnotation(CustomSerde.Deserializable).annotationClassValue("using").get().name == "python.ReversePointSerde"
        introspection.getConstructorArguments()[0].getAnnotationMetadata().getAnnotation(CustomSerde.Serializable).annotationClassValue("using").get().name == "python.ReversePointSerde"

        and: "a Java class and a Python class as the value of a class member"
        introspection.getRequiredProperty("other", Object).getAnnotation(CustomSerializer).annotationClassValue("using").get().name == "io.micronaut.python.annotation.processing.test.constants.MapperNames"
        introspection.getRequiredProperty("other", Object).stringValue(CustomSerializer, "name").get() == "other"
        introspection.getRequiredProperty("custom", Object).getAnnotation(CustomSerializer).annotationClassValue("using").get().name == "python.ReversePointSerde"
        !introspection.getRequiredProperty("plain", Object).hasAnnotation(CustomSerializer)
        !introspection.getRequiredProperty("plain", Object).hasAnnotation(CustomSerde.Serializable)

        cleanup:
        context?.close()
    }

    void "test a class attribute of a Python class from another compilation is left to the runtime"() {
        when: "the generated class of the other compilation declares no field for the attribute"
        def routes = buildClassElement('''
from micronaut.http.annotation import Controller, Post
from io.micronaut.python.annotation.processing.test.constants import GeneratedPaths

@Controller("/generated")
class GeneratedController:

    @Post(GeneratedPaths.SAVE_PATH)
    def save(self) -> str:
        return "saved"
''', "GeneratedController") { ClassElement element ->
            element.getEnclosedElements(ElementQuery.ALL_METHODS.named("save")).collect { it.stringValue(Post).orElse(null) }
        }

        then: "compilation succeeds; the value is resolved when the Python module loads"
        routes.size() == 1

        when: "a decorator default and a ** expansion name an unresolvable member"
        buildContext('''
from jakarta.inject import Named, Singleton
from io.micronaut.python.annotation.processing.test.constants import MapperNames

@Singleton
@Named(**{"value": MapperNames.MISSING})
class PropertiesMapper:
    pass
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("MapperNames.MISSING")
        e.message.contains("[value]")
        e.message.contains("Named")
    }

    void "test an unresolvable constant member reports the member"() {
        when:
        buildContext('''
from jakarta.inject import Named, Singleton
from io.micronaut.python.annotation.processing.test.constants import MapperNames

@Singleton
@Named(MapperNames.MISSING)
class PropertiesMapper:
    pass
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("MapperNames.MISSING")
        e.message.contains("value")
        e.message.contains("Named")
    }
}
