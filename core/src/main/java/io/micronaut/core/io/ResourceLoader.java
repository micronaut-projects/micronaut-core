package io.micronaut.core.io;

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
     * Obtains the URL to a given resource
     *
     * @param path The path
     * @return An optional resource
     */
    Optional<URL> getResource(String path);

    /**
     * Obtains all resources with the given name
     *
     * @param name The name of the resource
     * @return A stream of URLs
     */
    Stream<URL> getResources(String name);

    /**
     * @param path The path to a resource including a prefix
     *             appended by a colon. Ex (classpath:, file:)
     * @return Whether the given resource loader supports the prefix
     */
    boolean supportsPrefix(String path);

    /**
     * Constructs a new resource loader designed to load
     * resources from the given path. Requested resources
     * will be loaded within the context of the given path.
     *
     * @param basePath The path to load resources
     * @return The new {@link ResourceLoader}
     */
    ResourceLoader forBase(String basePath);

}
