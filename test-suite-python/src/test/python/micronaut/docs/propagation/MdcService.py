import java

from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

MdcPropagationContext = java.type("io.micronaut.context.propagation.slf4j.MdcPropagationContext")
MDC = java.type("org.slf4j.MDC")
PropagatedContext = java.type("io.micronaut.core.propagation.PropagatedContext")
UUID = java.type("java.util.UUID")


@Requires(property="mdc.example.service.enabled")
@Singleton
class MdcService:

    # tag::createUser[]
    def createUser(self, name: str) -> str:
        try:
            newUserId = UUID.randomUUID()
            MDC.put("userId", str(newUserId))
            return PropagatedContext.getOrEmpty() \
                .plus(MdcPropagationContext(MDC.getCopyOfContextMap())) \
                .propagateCall(lambda: self.createUserInternal(newUserId, name))
        finally:
            MDC.remove("userId")
    # end::createUser[]

    def createUserInternal(self, id, name: str) -> str:
        if MDC.get("userId") is None:
            raise RuntimeError("Missing userId")
        return "New user id: " + str(id) + " name: " + name
