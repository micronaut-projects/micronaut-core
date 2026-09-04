from typing import Annotated

from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .CodeValidator import CodeValidator


@MicronautTest
class RetainableSpec:
    context: Annotated[ApplicationContext, Inject] = None

    @Test
    def test_each_composed_annotation_reports_the_occurrence_it_introduced(self):
        definition = self.context.getBeanDefinition(CodeValidator)

        # tag::read[]
        min_length = definition.getAnnotation(
            "micronaut.docs.inject.retainable.MinLength"
        ).getStereotypes()[0]
        max_length = definition.getAnnotation(
            "micronaut.docs.inject.retainable.MaxLength"
        ).getStereotypes()[0]

        assert min_length.getAnnotationName() == "micronaut.docs.inject.retainable.Limit"
        assert min_length.getValues()["min"] == 3  # @Limit(min = 3)
        assert max_length.getValues()["max"] == 9  # @Limit(max = 9)
        # end::read[]

    @Test
    def test_the_flat_index_cannot_attribute_the_occurrences(self):
        names = self.context.getBeanDefinition(CodeValidator).getAnnotationNamesByStereotype(
            "micronaut.docs.inject.retainable.Limit"
        )

        assert list(names) == [
            "micronaut.docs.inject.retainable.MinLength",
            "micronaut.docs.inject.retainable.MaxLength",
        ]
