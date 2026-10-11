from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

from .Serializer import Serializer


@Requires(property="spec.name", value="BeanWatchSnippetsSpec")
@Singleton
class StringSerializer(Serializer):

    def supports(self, value: object) -> bool:
        return isinstance(value, str)

    def serialize(self, value: object) -> str:
        return '"' + str(value) + '"'
