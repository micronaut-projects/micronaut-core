package org.particleframework.context.env;

import org.particleframework.context.converters.StringArrayToClassArrayConverter;
import org.particleframework.context.converters.StringToClassConverter;
import org.particleframework.core.value.MapPropertyResolver;
import org.particleframework.core.value.PropertyResolver;
import org.particleframework.core.annotation.Nullable;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.io.scan.CachingClassPathAnnotationScanner;
import org.particleframework.core.io.scan.ClassPathAnnotationScanner;
import org.particleframework.core.io.service.SoftServiceLoader;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.core.util.StringUtils;
import org.particleframework.inject.BeanConfiguration;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * <p>The default implementation of the {@link Environment} interface. Configures a named environment.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultEnvironment extends PropertySourcePropertyResolver implements Environment {

    private final Set<String> names;
    private final ClassLoader classLoader;
    private final Collection<String> packages = new ConcurrentLinkedQueue<>();
    private final ClassPathAnnotationScanner annotationScanner;
    private Collection<String> configurationIncludes = new HashSet<>();
    private Collection<String> configurationExcludes = new HashSet<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DefaultEnvironment(ClassLoader classLoader, String... names) {
        this(classLoader, ConversionService.SHARED, names);
    }

    public DefaultEnvironment(String... names) {
        this(DefaultEnvironment.class.getClassLoader(), ConversionService.SHARED, names);
    }

    public DefaultEnvironment(ClassLoader classLoader, ConversionService conversionService, String... names) {
        super(conversionService);
        this.names = names == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
        conversionService.addConverter(
                CharSequence.class, Class.class, new StringToClassConverter(classLoader)
        );
        conversionService.addConverter(
                Object[].class, Class[].class, new StringArrayToClassArrayConverter(classLoader)
        );
        this.classLoader = classLoader;
        this.annotationScanner = createAnnotationScanner(classLoader);
    }

    @Override
    public Stream<Class> scan(Class<? extends Annotation> annotation) {
        return annotationScanner.scan(annotation, getPackages());
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public boolean isActive(BeanConfiguration configuration) {
        String name = configuration.getName();
        return !configurationExcludes.contains(name) && (configurationIncludes.isEmpty() || configurationIncludes.contains(name));
    }

    @Override
    public DefaultEnvironment addPropertySource(PropertySource propertySource) {
        propertySources.add(propertySource);
        if(isRunning()) {
            processPropertySource(propertySource, false);
        }
        return this;
    }

    @Override
    public DefaultEnvironment addPropertySource(Map<String, ? super Object> values) {
        return (DefaultEnvironment) super.addPropertySource(values);
    }

    @Override
    public Environment addPackage(String pkg) {
        if(!this.packages.contains(pkg)) {
            this.packages.add(pkg);
        }
        return this;
    }

    @Override
    public Environment addConfigurationExcludes(@Nullable String... names) {
        if(names != null) {
            configurationExcludes.addAll(Arrays.asList(names));
        }
        return this;
    }

    @Override
    public Environment addConfigurationIncludes(String... names) {
        if(names != null) {
            configurationIncludes.addAll(Arrays.asList(names));
        }
        return this;
    }

    @Override
    public Collection<String> getPackages() {
        return Collections.unmodifiableCollection(packages);
    }

    @Override
    public Set<String> getActiveNames() {
        return this.names;
    }

    @Override
    public Environment start() {
        if(running.compareAndSet(false, true)) {
            ArrayList<PropertySource> propertySources = new ArrayList<>(this.propertySources);
            SoftServiceLoader<PropertySourceLoader> propertySourceLoaders = SoftServiceLoader.load(PropertySourceLoader.class);
            for (SoftServiceLoader.Service<PropertySourceLoader> loader : propertySourceLoaders) {
                if(loader.isPresent()) {
                    Optional<PropertySource> propertySource = loader.load().load(this);
                    propertySource.ifPresent(propertySources::add);

                }
            }
            propertySources.add(new SystemPropertiesPropertySource());
            propertySources.add(new EnvironmentPropertySource());
            OrderUtil.sort(propertySources);
            for (PropertySource propertySource : propertySources) {
                processPropertySource(propertySource, propertySource.hasUpperCaseKeys());
            }
        }
        return this;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Environment stop() {
        running.set(false);
        return this;
    }

    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context) {
        return conversionService.convert(object, targetType, context);
    }

    @Override
    public <S, T> Environment addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        conversionService.addConverter(sourceType, targetType, typeConverter);
        return this;
    }

    @Override
    public <S, T> Environment addConverter(Class<S> sourceType, Class<T> targetType, Function<S, T> typeConverter) {
        conversionService.addConverter(sourceType, targetType, typeConverter);
        return this;
    }

    /**
     * Creates the default annotation scanner
     *
     * @param classLoader The class loader
     * @return The scanner
     */
    protected ClassPathAnnotationScanner createAnnotationScanner(ClassLoader classLoader) {
        return new CachingClassPathAnnotationScanner(classLoader);
    }

}
