from typing import Annotated

from jakarta.inject import Singleton
from micronaut.context.annotation import AnnotationExpressionContext


@Singleton
class AnnotationContext:  # <2>
    def firstValue(self) -> str:
        return "first value"


@Singleton
class AnnotationMemberContext:  # <3>
    def secondValue(self) -> str:
        return "second value"


@AnnotationExpressionContext(AnnotationContext)  # <4>
def CustomAnnotation(
    value: Annotated[str, AnnotationExpressionContext(AnnotationMemberContext)] = "",  # <5>
):
    def decorator(bean):
        return bean

    return decorator


@Singleton
@CustomAnnotation(value="#{firstValue() + secondValue()}")  # <1>
class Example:
    ...
