import java

# tag::clazz[]
from jakarta.inject import Singleton
from micronaut.aop import MethodInvocationContext
from micronaut.http import MutableHttpRequest
from micronaut.http.client.bind import AnnotatedClientRequestBinder, ClientRequestUriContext

Class = java.type("java.lang.Class")
NameAuthorizationClass = java.type("micronaut.docs.http.client.bind.method.NameAuthorization")


@Singleton  # <1>
class NameAuthorizationBinder(AnnotatedClientRequestBinder):  # <2>

    def getAnnotationType(self) -> Class:
        return NameAuthorizationClass

    def bind(  # <3>
        self,
        context: MethodInvocationContext,
        uriContext: ClientRequestUriContext,
        request: MutableHttpRequest,
    ) -> None:
        value = context.getValue(NameAuthorizationClass)
        if value.isPresent():
            uriContext.addQueryParameter("name", str(value.get()))
# end::clazz[]
