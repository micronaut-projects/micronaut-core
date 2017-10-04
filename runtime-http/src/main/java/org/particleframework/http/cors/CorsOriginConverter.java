package org.particleframework.http.cors;

import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Responsible for converting a map of configuration to an instance
 * of {@link CorsOriginConfiguration}
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class CorsOriginConverter implements TypeConverter<Object, CorsOriginConfiguration> {

    private static final String ALLOWED_ORIGINS = "allowedOrigins";
    private static final String ALLOWED_METHODS = "allowedMethods";
    private static final String ALLOWED_HEADERS = "allowedHeaders";
    private static final String EXPOSED_HEADERS = "exposedHeaders";
    private static final String ALLOW_CREDENTIALS = "allowCredentials";
    private static final String MAX_AGE = "maxAge";

    private Optional<List<String>> getListValue(Object value) {
        Optional<List<String>> list;
        if (value instanceof String) {
            list = Optional.of(Arrays.asList((String) value));
        }
        else if (value instanceof List) {
            List<String> strings = ((List<Object>) value).stream().map(Object::toString).collect(Collectors.toList());
            list = Optional.of(strings);
        }
        else {
            list = Optional.empty();
        }
        return list;
    }

    @Override
    public Optional<CorsOriginConfiguration> convert(Object object, Class<CorsOriginConfiguration> targetType, ConversionContext context) {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        if (object instanceof Map) {
            Map mapConfig = (Map) object;
            if (mapConfig.containsKey(ALLOWED_ORIGINS)) {
                Object value = mapConfig.get(ALLOWED_ORIGINS);
                configuration.setAllowedOrigins(getListValue(value));
            }
            if (mapConfig.containsKey(ALLOWED_METHODS)) {
                Object value = mapConfig.get(ALLOWED_METHODS);
                configuration.setAllowedMethods(getListValue(value));
            }
            if (mapConfig.containsKey(ALLOWED_HEADERS)) {
                Object value = mapConfig.get(ALLOWED_HEADERS);
                configuration.setAllowedHeaders(getListValue(value));
            }
            if (mapConfig.containsKey(EXPOSED_HEADERS)) {
                Object value = mapConfig.get(EXPOSED_HEADERS);
                configuration.setExposedHeaders(getListValue(value));

            }
            if (mapConfig.containsKey(ALLOW_CREDENTIALS)) {
                Object value = mapConfig.get(ALLOW_CREDENTIALS);
                Optional<Boolean> allow;
                if (value instanceof Boolean) {
                    allow = Optional.of(((Boolean) value));
                }
                else {
                    allow = Optional.empty();
                }
                configuration.setAllowCredentials(allow);
            }
            if (mapConfig.containsKey(MAX_AGE)) {
                Object value = mapConfig.get(MAX_AGE);
                Optional<Long> maxAge;
                if (value instanceof Number) {
                    maxAge = Optional.of(((Number) value).longValue());
                }
                else {
                    maxAge = Optional.empty();
                }
                configuration.setMaxAge(maxAge);
            }
        }
        return Optional.of(configuration);
    }
}
