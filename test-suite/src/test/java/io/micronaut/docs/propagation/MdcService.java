package io.micronaut.docs.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.PropagatedContext;
import org.slf4j.MDC;

import java.util.UUID;

@Requires(property = "mdc.example.enabled")
public class MdcService {

    // tag::createUser[]
    public Long createUser(String name) {
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

    private Long createUserInternal(UUID id, String name) {
        return null;
    }

}
