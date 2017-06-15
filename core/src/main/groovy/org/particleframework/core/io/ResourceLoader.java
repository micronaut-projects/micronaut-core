package org.particleframework.core.io;

import java.io.InputStream;
import java.util.Optional;

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
}
