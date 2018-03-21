package io.micronaut.context.env.groovy;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads properties from a Groovy script
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GroovyPropertySourceLoader extends AbstractPropertySourceLoader {

    @Override
    public int getOrder() {
        return AbstractPropertySourceLoader.DEFAULT_POSITION;
    }


    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) throws IOException {
        ConfigurationEvaluator evaluator = new ConfigurationEvaluator();
        try {
            Map<String, Object> configurationValues = evaluator.evaluate(input);
            if(!configurationValues.isEmpty()) {
                finalMap.putAll(configurationValues);
            }
        }
        catch (Throwable e) {
            throw new ConfigurationException("Exception occurred reading configuration ["+name+"]: " + e.getMessage(), e);
        }

    }

    @Override
    protected Optional<InputStream> readInput(Environment environment, String fileName) {
        Stream<URL> urls = environment.getResources(fileName);
        Stream<URL> urlStream = urls.filter(url -> !url.getPath().contains("src/main/groovy"));
        Optional<URL> config = urlStream.findFirst();
        if(config.isPresent()) {
            return config.flatMap(url -> {
                try {
                    return Optional.of(url.openStream());
                } catch (IOException e) {
                    throw new ConfigurationException("Exception occurred reading configuration ["+fileName+"]: " + e.getMessage(), e);
                }
            });
        }
        return Optional.empty();
    }

    @Override
    public Set<String> getExtensions() {
        return Collections.singleton("groovy");
    }
}
