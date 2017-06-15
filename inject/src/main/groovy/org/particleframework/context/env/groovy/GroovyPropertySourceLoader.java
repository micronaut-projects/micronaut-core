package org.particleframework.context.env.groovy;

import org.particleframework.context.env.Environment;
import org.particleframework.context.env.PropertySource;
import org.particleframework.context.env.PropertySourceLoader;

import java.util.Optional;

/**
 * Loads properties from a Groovy script
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GroovyPropertySourceLoader implements PropertySourceLoader {
    @Override
    public Optional<PropertySource> load(Environment environment) {
        return Optional.empty();
    }
}
