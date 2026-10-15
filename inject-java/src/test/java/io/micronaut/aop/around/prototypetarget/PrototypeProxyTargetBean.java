package io.micronaut.aop.around.prototypetarget;

import io.micronaut.aop.around.proxytarget.Mutating;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * A prototype-scoped bean proxied with {@code proxyTarget = true}.
 *
 * <p>The combination matters because the proxy resolves its target through the same
 * resolution context that is constructing it. At singleton scope the target comes from
 * cache and never re-enters construction; at prototype scope there is nothing cached.
 */
@Prototype
@Requires(property = "spec.name", value = "PrototypeProxyTargetTest")
public class PrototypeProxyTargetBean {

    private final Collaborator collaborator;

    public PrototypeProxyTargetBean(Collaborator collaborator) {
        this.collaborator = collaborator;
    }

    @Mutating("name")
    public String test(String name) {
        return "Name is " + name + " via " + collaborator.name();
    }
}
