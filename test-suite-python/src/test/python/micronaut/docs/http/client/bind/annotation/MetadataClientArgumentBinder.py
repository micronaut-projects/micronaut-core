import java

# tag::clazz[]
from jakarta.inject import Singleton
from micronaut.core.convert import ArgumentConversionContext
from micronaut.core.naming import NameUtils
from micronaut.core.util import StringUtils
from micronaut.http import MutableHttpRequest
from micronaut.http.client.bind import AnnotatedClientArgumentRequestBinder, ClientRequestUriContext

Class = java.type("java.lang.Class")
MetadataClass = java.type("micronaut.docs.http.client.bind.annotation.Metadata")


@Singleton
class MetadataClientArgumentBinder(AnnotatedClientArgumentRequestBinder):

    def getAnnotationType(self) -> Class:
        return MetadataClass

    def bind(
        self,
        context: ArgumentConversionContext,
        uriContext: ClientRequestUriContext,
        value: object,
        request: MutableHttpRequest,
    ) -> None:
        if hasattr(value, "entrySet"):
            for entry in value.entrySet():
                key = NameUtils.hyphenate(StringUtils.capitalize(str(entry.getKey())), False)
                request.header("X-Metadata-" + key, str(entry.getValue()))
# end::clazz[]
