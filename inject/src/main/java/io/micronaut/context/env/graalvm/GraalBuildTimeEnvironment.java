package io.micronaut.context.env.graalvm;

import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.env.DefaultPropertyPlaceholderResolver;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.env.PropertyResolverDelegate;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.context.env.ResourceLoaderDelegate;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Internal
public final class GraalBuildTimeEnvironment implements Environment, PropertyResolverDelegate, ResourceLoaderDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(GraalBuildTimeEnvironment.class);
    private static final List<String> DEFAULT_CONFIG_LOCATIONS = Arrays.asList("classpath:/", "file:config/");

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ConversionService<?> conversionService;
    private final PropertySourcePropertyResolver propertySourcePropertyResolver;
    private final PropertyPlaceholderResolver propertyPlaceholderResolver;
    private final ClassPathResourceLoader resourceLoader;
    private final Collection<String> configLocations;

    public static PropertySourcePropertyResolver PRELOADED;

    public GraalBuildTimeEnvironment(@NonNull ApplicationContextConfiguration configuration, PropertySourcePropertyResolver propertySourcePropertyResolver) {
        this.conversionService = configuration.getConversionService();
        this.propertySourcePropertyResolver = propertySourcePropertyResolver;
        this.propertyPlaceholderResolver = new DefaultPropertyPlaceholderResolver(this, conversionService);
        this.resourceLoader = configuration.getResourceLoader();

        List<String> configLocations = configuration.getOverrideConfigLocations() == null ?
                new ArrayList<>(DEFAULT_CONFIG_LOCATIONS) : configuration.getOverrideConfigLocations();
        // Search config locations in reverse order
        Collections.reverse(configLocations);
        this.configLocations = configLocations;
    }

    @Override
    public PropertyResolver getPropertyResolverDelegate() {
        return propertySourcePropertyResolver;
    }

    @Override
    public ResourceLoader getResourceLoaderDelegate() {
        return resourceLoader;
    }

    @Override
    public Set<String> getActiveNames() {
        return Collections.emptySet();
    }

    @Override
    public Collection<PropertySource> getPropertySources() {
        return null;
    }

    @Override
    public Environment addPropertySource(PropertySource propertySource) {
        throw new IllegalArgumentException("Not supported");
    }

    @Override
    public Environment removePropertySource(PropertySource propertySource) {
        throw new IllegalArgumentException("Not supported");
    }

    @Override
    public Environment addPackage(String pkg) {
        throw new IllegalArgumentException("Not supported");
    }

    @Override
    public Environment addConfigurationExcludes(String... names) {
        throw new IllegalArgumentException("Not supported");
    }

    @Override
    public Environment addConfigurationIncludes(String... names) {
        throw new IllegalArgumentException("Not supported");
    }

    @Override
    public Collection<String> getPackages() {
        throw new IllegalArgumentException("Not supported");
    }

    @Override
    public PropertyPlaceholderResolver getPlaceholderResolver() {
        return propertyPlaceholderResolver;
    }

    @Override
    public Map<String, Object> refreshAndDiff() {
        throw new IllegalArgumentException("Not supported");
    }

    @Override
    public boolean isActive(BeanConfiguration configuration) {
        return true;
    }

    @Override
    public Collection<PropertySourceLoader> getPropertySourceLoaders() {
        return Collections.emptyList();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Environment start() {
        if (running.compareAndSet(false, true)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Starting environment {} for active names {}", this, getActiveNames());
            }
        }
        return this;
    }

    @Override
    public Environment stop() {
        running.set(false);
        propertySourcePropertyResolver.resetProperties();
        return this;
    }

    @Override
    public <S, T> Environment addConverter(Class<S> sourceType, Class<T> targetType, Function<S, T> typeConverter) {
        conversionService.addConverter(sourceType, targetType, typeConverter);
        return this;
    }

    @Override
    public <S, T> Environment addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        conversionService.addConverter(sourceType, targetType, typeConverter);
        return this;
    }

    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context) {
        return conversionService.convert(object, targetType, context);
    }

    @Override
    public <S, T> boolean canConvert(Class<S> sourceType, Class<T> targetType) {
        return conversionService.canConvert(sourceType, targetType);
    }

}
