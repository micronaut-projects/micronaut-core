package io.micronaut.reflection;

import io.micronaut.core.annotation.AccessorsStyle;

/**
 * An interface naming its own read prefix, stood for by a {@link java.lang.reflect.Proxy}: what a property of the
 * proxy is depends on the annotations of the interface, which the proxy class does not carry.
 */
@AccessorsStyle(readPrefixes = "read")
public interface ProxiedStyled {

    String readTitle();

    String getIgnored();
}
