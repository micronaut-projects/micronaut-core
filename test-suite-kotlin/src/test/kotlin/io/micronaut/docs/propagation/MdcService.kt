package io.micronaut.docs.propagation

import io.micronaut.context.annotation.Requires
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.PropagatedContext
import jakarta.inject.Singleton
import org.slf4j.MDC
import java.util.UUID
import java.util.function.Supplier

@Requires(property = "mdc.example.service.enabled")
@Singleton
class MdcService {

    // tag::createUser[]
    fun createUser(name: String): String {
        try {
            val newUserId = UUID.randomUUID()
            MDC.put("userId", newUserId.toString())
            return PropagatedContext.getOrEmpty()
                .plus(MdcPropagationContext())
                .propagate(Supplier { createUserInternal(newUserId, name) })
        } finally {
            MDC.remove("userId")
        }
    }
    // end::createUser[]

    private fun createUserInternal(id: UUID, name: String): String {
        checkNotNull(MDC.get("userId")) { "Missing userId" }
        return "New user id: $id name: $name"
    }
}
