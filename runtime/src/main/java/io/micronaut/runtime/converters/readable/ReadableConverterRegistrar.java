package io.micronaut.runtime.converters.readable;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.Readable;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;

import javax.inject.Singleton;
import java.net.URL;
import java.util.Optional;

@Singleton
public class ReadableConverterRegistrar implements TypeConverterRegistrar {

    private final ResourceResolver resourceResolver;

    /**
     * Default constructor.
     *
     * @param resourceResolver The resource resolver
     */
    protected ReadableConverterRegistrar(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }


    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                CharSequence.class,
                Readable.class,
                (object, targetType, context) -> {
                    String pathStr = object.toString();
                    Optional<ResourceLoader> supportingLoader = resourceResolver.getSupportingLoader(pathStr);
                    if (!supportingLoader.isPresent()) {
                        context.reject(pathStr, new ConfigurationException(
                                "No supported resource loader for path [" + pathStr + "]. Prefix the path with a supported prefix such as 'classpath:' or 'file:'"
                        ));
                        return Optional.empty();
                    } else {
                        final Optional<URL> resource = resourceResolver.getResource(pathStr);
                        if (resource.isPresent()) {
                            return Optional.of(Readable.of(resource.get()));
                        } else {
                            context.reject(object, new ConfigurationException("No resource exists for value: " + object));
                            return Optional.empty();
                        }
                    }

                }
        );
    }
}
