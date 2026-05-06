# tag::clazz[]
from micronaut.context.annotation import DefaultImplementation

from .DefaultResponseStrategy import DefaultResponseStrategy


@DefaultImplementation(DefaultResponseStrategy)
class ResponseStrategy:
    pass
# end::clazz[]
