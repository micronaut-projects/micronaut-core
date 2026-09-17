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
package io.micronaut.python.annotation.processing.test.visitorintegration

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import jakarta.validation.ConstraintViolationException

/**
 * Constraints on the type arguments of a parameter ({@code list[Annotated[str, NotBlank]]}) are enforced like
 * {@code List<@NotBlank String>} in Java: the validation visitor marks the type argument and the marker reaches
 * the runtime argument metadata.
 */
class TypeArgumentConstraintSpec extends AbstractPythonTypeElementSpec {

    private static final String ANN_VALIDATED_ELEMENT = "io.micronaut.validation.annotation.ValidatedElement"

    void "test constraints on collection type arguments are validated"() {
        given:
        def context = buildContext('''
from typing import Annotated

from jakarta.inject import Singleton
from jakarta.validation.constraints import Min, NotBlank
from micronaut.validation import Validated


@Validated
@Singleton
class BookInfoService:
    def set_book_authors(self, book_name: Annotated[str, NotBlank], authors: list[Annotated[str, NotBlank]]) -> int:
        return len(authors)

    def set_book_section_pages(self, book_name: Annotated[str, NotBlank], section_start_pages: dict[Annotated[str, NotBlank], Annotated[int, Min(1)]]) -> int:
        return len(section_start_pages)
''', true)
        def definition = getBeanDefinition(context, "python.BookInfoService")
        def service = getBean(context, "python.BookInfoService")

        when:
        def authorsArgument = definition.getRequiredMethod("set_book_authors", String, List).arguments[1]
        def pagesArgument = definition.getRequiredMethod("set_book_section_pages", String, Map).arguments[1]

        then:
        authorsArgument.typeParameters[0].annotationMetadata.hasStereotype("jakarta.validation.Constraint")
        authorsArgument.typeParameters[0].annotationMetadata.hasAnnotation(ANN_VALIDATED_ELEMENT)
        pagesArgument.typeParameters[0].annotationMetadata.hasAnnotation(ANN_VALIDATED_ELEMENT)
        pagesArgument.typeParameters[1].annotationMetadata.hasAnnotation(ANN_VALIDATED_ELEMENT)

        when:
        service.set_book_authors("", ["Me"])

        then:
        def e0 = thrown(ConstraintViolationException)
        e0.message == "set_book_authors.book_name: must not be blank"

        when:
        service.set_book_authors("My Book", ["Me", ""])

        then:
        def e = thrown(ConstraintViolationException)
        e.message == "set_book_authors.authors[1]<list element>: must not be blank"

        when:
        service.set_book_section_pages("My Book", ["": 1])

        then:
        def e2 = thrown(ConstraintViolationException)
        e2.message == "set_book_section_pages.section_start_pages[]<map key>: must not be blank"

        when:
        service.set_book_section_pages("My Book", ["Intro": 0])

        then:
        def e3 = thrown(ConstraintViolationException)
        e3.message == "set_book_section_pages.section_start_pages[Intro]<map value>: must be greater than or equal to 1"

        when:
        int count = service.set_book_authors("My Book", ["Me", "You"])

        then:
        count == 2

        cleanup:
        context?.close()
    }

    void "test a constraint on a nullable type argument is kept next to the nullability"() {
        given: "the constraint of Annotated[str | None, NotBlank] sits on the union node"
        def context = buildContext('''
from typing import Annotated

from jakarta.inject import Singleton
from jakarta.validation.constraints import NotBlank
from micronaut.validation import Validated


@Validated
@Singleton
class EditorService:
    def set_editors(self, editors: list[Annotated[str | None, NotBlank]]) -> int:
        return len(editors)

    def set_reviewers(self, reviewers: list[str | None]) -> int:
        return len([reviewer for reviewer in reviewers if reviewer is not None])
''', true)
        def definition = getBeanDefinition(context, "python.EditorService")
        def service = getBean(context, "python.EditorService")

        when:
        def editorsElement = definition.getRequiredMethod("set_editors", List).arguments[0].typeParameters[0].annotationMetadata
        def reviewersElement = definition.getRequiredMethod("set_reviewers", List).arguments[0].typeParameters[0].annotationMetadata

        then: "the type argument is nullable and constrained"
        editorsElement.hasAnnotation(AnnotationUtil.NULLABLE)
        editorsElement.hasStereotype("jakarta.validation.Constraint")
        editorsElement.hasAnnotation(ANN_VALIDATED_ELEMENT)

        and: "a nullable type argument without a constraint is only nullable"
        reviewersElement.hasAnnotation(AnnotationUtil.NULLABLE)
        !reviewersElement.hasStereotype("jakarta.validation.Constraint")
        !reviewersElement.hasAnnotation(ANN_VALIDATED_ELEMENT)

        when:
        service.set_editors(["Me", ""])

        then:
        def e = thrown(ConstraintViolationException)
        e.message == "set_editors.editors[1]<list element>: must not be blank"

        when:
        int count = service.set_editors(["Me", "You"])
        int reviewers = service.set_reviewers(["Me", null])

        then:
        count == 2
        reviewers == 1

        cleanup:
        context?.close()
    }
}
