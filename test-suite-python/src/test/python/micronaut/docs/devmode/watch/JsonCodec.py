from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

from .Codec import Codec


@Requires(property="spec.name", value="BeanWatchSnippetsSpec")
@Singleton
class JsonCodec(Codec):

    def media_type(self) -> str:
        return "application/json"
