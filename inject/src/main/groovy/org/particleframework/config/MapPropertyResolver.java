package org.particleframework.config;

import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.DefaultConversionService;

import java.util.Map;
import java.util.Optional;

/**
 * A property resolver that resolves values from a map
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MapPropertyResolver implements PropertyResolver {
    private final Map<String, Object> map;
    private final ConversionService conversionService;

    public MapPropertyResolver(Map<String, Object> map) {
        this.map = map;
        this.conversionService = new DefaultConversionService();
    }

    public MapPropertyResolver(Map<String, Object> map, ConversionService conversionService) {
        this.map = map;
        this.conversionService = conversionService;
    }

    @Override
    public <T> Optional<T> getProperty(String name, Class<T> requiredType, Map<String, Class> typeArguments) {
        Object value = map.get(name);
        return conversionService.convert(value, requiredType, typeArguments);
    }
}
