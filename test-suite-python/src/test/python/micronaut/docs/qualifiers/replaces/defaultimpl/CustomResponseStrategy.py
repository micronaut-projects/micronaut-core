# tag::clazz[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Replaces

from .ResponseStrategy import ResponseStrategy


@Singleton
@Replaces(ResponseStrategy)
class CustomResponseStrategy(ResponseStrategy):
    pass
# end::clazz[]
