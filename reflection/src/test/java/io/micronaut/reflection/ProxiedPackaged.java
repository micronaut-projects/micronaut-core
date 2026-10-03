package io.micronaut.reflection;

import io.micronaut.core.annotation.Introspected;

/**
 * A package-private interface, whose {@link java.lang.reflect.Proxy} the JDK names after the package -
 * {@code io.micronaut.reflection.$ProxyN} - so that a pattern allowing the package matches the proxy class: what the
 * interface declares of itself must then win over what a configuration describes the package with, as it does for
 * a class.
 */
@Introspected(accessKind = Introspected.AccessKind.METHOD)
interface ProxiedPackaged {

    String getName();
}
