package io.micronaut.http.client.aop

import io.micronaut.context.env.PropertyExpressionResolver
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.value.PropertyResolver

import java.util.concurrent.atomic.AtomicInteger

/**
 * Resolves {@code ${counted.<property>}} to the value of the property and counts the resolutions,
 * so a test can tell how often a placeholder is resolved.
 */
class CountingPropertyExpressionResolver implements PropertyExpressionResolver {

    static final String PREFIX = 'counted.'
    static final AtomicInteger RESOLUTIONS = new AtomicInteger()

    @Override
    <T> Optional<T> resolve(PropertyResolver propertyResolver, ConversionService conversionService, String expression, Class<T> requiredType) {
        if (!expression.startsWith(PREFIX)) {
            return Optional.empty()
        }
        RESOLUTIONS.incrementAndGet()
        return propertyResolver.getProperty(expression.substring(PREFIX.length()), requiredType)
    }
}
