package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.ValueCoercible
import org.graalvm.polyglot.Context
import spock.lang.Stepwise

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
}
