package io.micronaut.python.annotation.processing.test

import example.reflective.ClassRetainedMarker
import example.reflective.ReflectiveAccessor
import example.reflective.ReflectiveColumn
import example.reflective.ReflectiveMapping
import example.reflective.ReflectiveTable

import java.lang.reflect.Modifier

/**
 * The generated Java class of a Python class carries the runtime annotations of Java annotation types
 * (JPA, JAXB, Bean Validation, ...) so that frameworks reading them reflectively see them, together with
 * the no-argument constructor such frameworks instantiate the class with.
 */
class RuntimeAnnotationStubSpec extends AbstractPythonTypeElementSpec {

    private static final String ENTITY = '''
from dataclasses import dataclass, field
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected
from example.reflective import ClassRetainedMarker, ReflectiveAccessor, ReflectiveColumn, ReflectiveMapping, ReflectiveTable


def next_stamp() -> str:
    return "stamped"


@Introspected
@Singleton
@ClassRetainedMarker
@ReflectiveMapping(name="books", fetch=ReflectiveMapping.Fetch.LAZY, target=str, columns=[ReflectiveColumn("title"), ReflectiveColumn("pages")])
@ReflectiveTable(name="books")
@dataclass
class Book:
    title: str | None = "untitled"
    pages: int = 10
    tags: list[str] = field(default_factory=list)
    stamp: str = field(default_factory=next_stamp)
    id: Annotated[int | None, ReflectiveMapping(name="book_id"), ReflectiveColumn(value="id", length=10, precision=2, nullable=False), ReflectiveAccessor("id")] = None

    @ReflectiveMapping(name="summary")
    def summary(self) -> str:
        return f"{self.title} ({self.pages})"


@Introspected
@ReflectiveTable(name="broken")
class Reader:
    def __init__(self, name: str | None = None):
        self._name = name

    @property
    @ReflectiveMapping(name="reader_name")
    def name(self) -> str | None:
        return self._name

    @name.setter
    @ReflectiveMapping(name="set_reader_name")
    def name(self, value: str | None):
        self._name = value
'''

    void "runtime annotations of Java annotation types are copied onto the generated class and its fields"() {
        given:
        def context = buildContext(ENTITY)
        Class<?> bookClass = context.classLoader.loadClass("python.Book")

        when:
        ReflectiveMapping mapping = bookClass.getAnnotation(ReflectiveMapping)

        then: "the class annotation is copied with all of its members"
        mapping != null
        mapping.name() == "books"
        mapping.fetch() == ReflectiveMapping.Fetch.LAZY
        mapping.target() == String
        mapping.columns()*.value() == ["title", "pages"]

        and: "Micronaut and injection annotations stay in the annotation metadata, they are not needed on the class"
        bookClass.getAnnotation(io.micronaut.core.annotation.Introspected) == null
        bookClass.getAnnotation(jakarta.inject.Singleton) == null

        and: "annotations without runtime retention are not copied"
        bookClass.getAnnotation(ClassRetainedMarker) == null

        when:
        def idField = bookClass.getDeclaredField("id")

        then: "the annotations of a declared attribute are placed on the generated field"
        idField.getAnnotation(ReflectiveMapping).name() == "book_id"
        idField.getAnnotation(ReflectiveColumn).value() == "id"
        idField.getAnnotation(ReflectiveColumn).length() == 10
        idField.getAnnotation(ReflectiveColumn).precision() == 2L
        !idField.getAnnotation(ReflectiveColumn).nullable()
        bookClass.getDeclaredField("title").getAnnotation(ReflectiveMapping) == null

        and: "an annotation whose target excludes fields is not placed on the field"
        idField.getAnnotation(ReflectiveAccessor) == null

        and: "the accessors of a declared attribute carry no annotations"
        bookClass.getMethod("getId").getAnnotation(ReflectiveMapping) == null
        bookClass.getMethod("setId", Integer).getAnnotation(ReflectiveMapping) == null

        cleanup:
        context?.close()
    }

    void "the Python state of the generated class is transient"() {
        given:
        def context = buildContext(ENTITY)
        Class<?> bookClass = context.classLoader.loadClass("python.Book")

        def properties = ["title", "pages", "tags", "stamp", "id"]
        def internalFields = bookClass.declaredFields.findAll { !Modifier.isStatic(it.modifiers) && !(it.name in properties) }

        expect: "reflection-based frameworks (JPA field access, Java serialization) skip the Python state"
        internalFields*.name.contains("graalpyInternalValue")
        internalFields.every { Modifier.isTransient(it.modifiers) }
        bookClass.declaredFields.findAll { it.name in properties }.every { !Modifier.isTransient(it.modifiers) }

        cleanup:
        context?.close()
    }

    void "a field-backed class gets a no-argument constructor applying the constant Python defaults"() {
        given:
        def context = buildContext(ENTITY)
        Class<?> bookClass = context.classLoader.loadClass("python.Book")

        when: "instantiated the way JPA does, then populated through the fields"
        def book = bookClass.getConstructor().newInstance()

        then: "the literal defaults are applied"
        book.title == "untitled"
        book.pages == 10
        book.id == null

        and: "the builtin list, dict and set factories are applied as empty collections"
        book.tags == []

        and: "a default factory the generated code cannot reproduce leaves the field unset for the framework to fill"
        book.stamp == null

        when: "the fields are populated reflectively and the object crosses into Python"
        bookClass.getDeclaredField("title").set(book, "The Stand")
        bookClass.getDeclaredField("id").set(book, 42)

        then:
        book.summary() == "The Stand (10)"
        book.asPolyglotValue().getMember("id").asInt() == 42
        book.stamp == null

        cleanup:
        context?.close()
    }

    void "a member the annotation type does not declare or cannot hold is left out of the copied annotation"() {
        given:
        def context = buildContext(ENTITY)
        Class<?> bookClass = context.classLoader.loadClass("python.Book")
        Class<?> readerClass = context.classLoader.loadClass("python.Reader")

        when:
        ReflectiveTable table = bookClass.getAnnotation(ReflectiveTable)

        then: "the annotation is copied with the members the type declares"
        table.name() == "books"

        and: "a member with a default keeps it when its value cannot be written"
        table.catalog() == ""

        and: "the annotation metadata still serves the members added by the transformer"
        getBeanIntrospection(context, "python.Book").getAnnotation(ReflectiveTable).stringValue("schema").get() == "public"
        getBeanIntrospection(context, "python.Book").getAnnotation(ReflectiveTable).intValue("catalog").getAsInt() == 42

        and: "an annotation whose member without a default cannot be written is not copied"
        readerClass.getAnnotation(ReflectiveTable) == null
        getBeanIntrospection(context, "python.Reader").getAnnotation(ReflectiveTable).stringValue("schema").get() == "public"

        cleanup:
        context?.close()
    }

    void "runtime annotations of Python accessors and methods are placed on the generated accessors and bridges"() {
        given:
        def context = buildContext(ENTITY)
        Class<?> bookClass = context.classLoader.loadClass("python.Book")
        Class<?> readerClass = context.classLoader.loadClass("python.Reader")

        expect: "a bridged method keeps its annotation"
        bookClass.getMethod("summary").getAnnotation(ReflectiveMapping).name() == "summary"

        and: "a Python property getter and setter keep theirs (JPA property access)"
        readerClass.getMethod("name").getAnnotation(ReflectiveMapping).name() == "reader_name"
        readerClass.getMethod("name", String).getAnnotation(ReflectiveMapping).name() == "set_reader_name"

        cleanup:
        context?.close()
    }
}
