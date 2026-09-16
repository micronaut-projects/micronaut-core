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

import org.graalvm.polyglot.Context

/**
 * Value semantics of the generated stubs of dataclasses.
 */
class DataclassEqualitySpec extends AbstractPythonTypeElementSpec {

    void "test hashCode of a bidirectional association does not recurse"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass, field
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Book:
    title: str
    reviews: "list[Review]" = field(default_factory=list)

@Introspected
@dataclass
class Review:
    content: str
    book: "Book | None" = None
'''

        when:
        def context = buildContext(pythonCode)
        def bookIntrospection = getBeanIntrospection(context, "python.Book")
        def reviewIntrospection = getBeanIntrospection(context, "python.Review")
        def book = bookIntrospection.instantiate("Dummy Book", [])
        def review = reviewIntrospection.instantiate("Lorem Ipsum", book)
        book.reviews.add(review)
        def tracked = new HashSet<Object>()

        then: "the entities can be tracked in a hash set, as Micronaut Data does for cascaded associations"
        tracked.add(book)
        tracked.add(review)
        tracked.contains(book)
        tracked.contains(review)
        book == book
        review == review

        and: "equal objects have equal hashes"
        reviewIntrospection.instantiate("Lorem Ipsum", book) == review
        reviewIntrospection.instantiate("Lorem Ipsum", book).hashCode() == review.hashCode()
        reviewIntrospection.instantiate("Other", book) != review

        cleanup:
        context?.close()
    }

    void "test nested list attributes are compared by value"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass, field

@dataclass
class Elephant:
    name: str
    values: list[list[int]] = field(default_factory=list)
    tags: dict[str, list[str]] = field(default_factory=dict)
    sizes: set[int] = field(default_factory=set)
'''

        when:
        def context = buildContext(pythonCode, true)
        Context polyglot = context.getBean(Context)
        def elephantType = context.classLoader.loadClass("python.Elephant")
        def first = polyglot.eval("python", "Elephant('Dumbo', [[1] * 3] * 3, {'a': ['x']}, {1, 2})").as(elephantType)
        def second = polyglot.eval("python", "Elephant('Dumbo', [[1, 1, 1], [1, 1, 1], [1, 1, 1]], {'a': ['x']}, {2, 1})").as(elephantType)

        then: "every call converts the Python data to Java collections with value equality (Objects.equals: Groovy compares lists leniently)"
        Objects.equals(first.getValues(), [[1, 1, 1], [1, 1, 1], [1, 1, 1]])
        Objects.equals(first.getValues(), second.getValues())
        first.getValues().hashCode() == second.getValues().hashCode()
        first.getValues()[0] instanceof List
        Objects.equals(first.getTags(), [a: ["x"]])
        Objects.equals(first.getTags(), second.getTags())
        Objects.equals(first.getSizes(), [1, 2] as Set)
        Objects.equals(first.getSizes(), second.getSizes())

        cleanup:
        context?.close()
    }
}
