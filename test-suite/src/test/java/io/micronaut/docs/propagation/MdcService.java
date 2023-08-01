package io.micronaut.docs.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.PropagatedContext;
import jakarta.inject.Singleton;
import org.slf4j.MDC;

import java.util.UUID;

@Requires(property = "mdc.example.service.enabled")
@Singleton
public class MdcService {

    // tag::createUser[]
    public String createUser(String name) {
        try {
            UUID newUserId = UUID.randomUUID();
            MDC.put("userId", newUserId.toString());
            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new MdcPropagationContext()).propagate()) {
                return createUserInternal(newUserId, name);
            }
        } finally {
            MDC.remove("userId");
        }
    }
    // end::createUser[]

    private String createUserInternal(UUID id, String name) {
        if (MDC.get("userId") == null) {
            throw new IllegalStateException("Missing userId");
        }
        return "New user id: " + id + " name: " + name;
    }

}
