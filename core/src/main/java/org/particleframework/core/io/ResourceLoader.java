package org.particleframework.core.io;

import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Basic abstraction over resource loading
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ResourceLoader {

    /**
     * Obtains a resource as a stream
     *
     * @param path The path
     * @return An optional resource
     */
    Optional<InputStream> getResourceAsStream(String path);

    /**
     * Obtains a resource URL
     *
     * @param path The path
     * @return An optional resource
     */
    Optional<URL> getResource(String path);

    /**
     * Obtains a stream of resource URLs
     *
     * @param path The path
     * @return A resource stream
     */
    Stream<URL> getResources(String path);
}
