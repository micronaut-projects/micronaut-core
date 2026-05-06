import java

# tag::clazz[]
from jakarta.inject import Singleton
from micronaut.core.convert import ArgumentConversionContext
from micronaut.core.type import Argument
from micronaut.http import MutableHttpRequest
from micronaut.http.client.bind import ClientRequestUriContext, TypedClientArgumentRequestBinder

MetadataClass = java.type("micronaut.docs.http.client.bind.type.Metadata")


@Singleton
class MetadataClientArgumentBinder(TypedClientArgumentRequestBinder):

    def argumentType(self) -> Argument:
        return Argument.of(MetadataClass)

    def bind(
        self,
        context: ArgumentConversionContext,
        uriContext: ClientRequestUriContext,
        value: MetadataClass,
        request: MutableHttpRequest,
    ) -> None:
        request.header("X-Metadata-Version", str(value.version))
        request.header("X-Metadata-Deployment-Id", str(value.deployment_id))
# end::clazz[]
