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
}
