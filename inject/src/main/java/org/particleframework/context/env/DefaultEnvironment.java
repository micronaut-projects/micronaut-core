package org.particleframework.context.env;

import org.particleframework.config.MapPropertyResolver;
import org.particleframework.config.PropertyResolver;
import org.particleframework.core.annotation.Nullable;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.io.scan.CachingClassPathAnnotationScanner;
import org.particleframework.core.io.scan.ClassPathAnnotationScanner;
import org.particleframework.core.io.service.SoftServiceLoader;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.inject.BeanConfiguration;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>The default implementation of the {@link Environment} interface. Configures a named environment.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultEnvironment implements Environment {

    private final String name;
    private final ConversionService conversionService;
    // properties are stored in an array of maps organized by character in the alphabet
    // this allows optimization of searches by prefix
    private final Map<String,Object>[] catalog = new Map[57];
    private final Collection<PropertySource> propertySources = new ConcurrentLinkedQueue<>();
    private final ClassLoader classLoader;
    private final Collection<String> packages = new ConcurrentLinkedQueue<>();
    private final Map<Class, SoftServiceLoader> serviceMap = new ConcurrentHashMap<>();
    private final ClassPathAnnotationScanner annotationScanner;
    private Collection<String> configurationIncludes = new HashSet<>();
    private Collection<String> configurationExcludes = new HashSet<>();


    public DefaultEnvironment(String name, ClassLoader classLoader) {
        this(name,classLoader, ConversionService.SHARED);
    }

    public DefaultEnvironment(String name) {
        this(name,DefaultEnvironment.class.getClassLoader(), ConversionService.SHARED);
    }

    public DefaultEnvironment(String name, ClassLoader classLoader, ConversionService conversionService) {
        this.name = name == null ? "default" : name;
        this.conversionService = conversionService;
        this.classLoader = classLoader;
        this.annotationScanner = createAnnotationScanner(classLoader);

        ServiceLoader<PropertySourceLoader> propertySources = ServiceLoader.load(PropertySourceLoader.class);
        for (PropertySourceLoader loader : propertySources) {
            Optional<PropertySource> propertySource = loader.load(this);
            propertySource.ifPresent(this::addPropertySource);
        }

        addPropertySource(new MapPropertySource(System.getProperties()));
        addPropertySource(new MapPropertySource(System.getenv()) {
            @Override
            public boolean hasUpperCaseKeys() {
                return true;
            }
        });
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
    public Environment addPropertySource(PropertySource propertySource) {
        propertySources.add(propertySource);
        return this;
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
    public String getName() {
        return this.name;
    }

    @Override
    public <T> Optional<T> getProperty(String name, Class<T> requiredType, ConversionContext context) {
        if(name.length() == 0) {
            return Optional.empty();
        }
        else {
            Map<String,Object> entries = resolveEntriesForKey(name, false);
            if(entries != null) {
                Object value = entries.get(name);
                if(value != null) {
                    return conversionService.convert(value, requiredType, context);
                }
                else if(Map.class.isAssignableFrom(requiredType)) {
                    Map<String, Object> subMap = resolveSubMap(name, entries);
                    return conversionService.convert(subMap, requiredType, context);
                }
                else if(PropertyResolver.class.isAssignableFrom(requiredType)) {
                    Map<String, Object> subMap = resolveSubMap(name, entries);
                    return Optional.of((T) new MapPropertyResolver(subMap, conversionService));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Environment start() {
        ArrayList<PropertySource> propertySources = new ArrayList<>(this.propertySources);
        OrderUtil.sort(propertySources);
        for (PropertySource propertySource : propertySources) {
            processPropertySource(propertySource, propertySource.hasUpperCaseKeys());
        }
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

    @Override
    public Environment stop() {
        return this;
    }

    @Override
    public <T> Iterable<T> findServices(Class<T> type, Predicate<String> condition) {
        SoftServiceLoader<T> services =  serviceMap
                                            .computeIfAbsent(type, (serviceType)-> SoftServiceLoader.load(serviceType, getClassLoader(), condition) );
        Iterator<SoftServiceLoader.Service<T>> iterator = services.iterator();
        return () -> new Iterator<T>() {
            SoftServiceLoader.Service<T> nextService = null;
            @Override
            public boolean hasNext() {
                if(nextService != null) return true;
                while(iterator.hasNext() && (nextService == null || !nextService.isPresent())) {
                    nextService = iterator.next();
                }
                return nextService != null;
            }

            @Override
            public T next() {
                if(!hasNext()) {
                    throw new NoSuchElementException();
                }
                SoftServiceLoader.Service<T> next = this.nextService;
                nextService = null;
                return next.load();
            }
        };
    }

    protected Map<String, Object> resolveSubMap(String name, Map<String, Object> entries) {
        // special handling for maps for resolving sub keys
        Map<String, Object> subMap = new LinkedHashMap<>();
        String prefix = name + '.';
        for (Map.Entry<String, Object> map : entries.entrySet()) {
            if (map.getKey().startsWith(prefix)) {
                String subMapKey = map.getKey().substring(prefix.length());
                int index = subMapKey.indexOf('.');
                if (index == -1) {
                    subMap.put(subMapKey, map.getValue());
                } else {
                    String mapKey = subMapKey.substring(0, index);
                    if (!subMap.containsKey(mapKey)) {
                        subMap.put(mapKey, new LinkedHashMap<>());
                    }
                    Map<String, Object> nestedMap = (Map<String, Object>) subMap.get(mapKey);
                    nestedMap.put(subMapKey.substring(index + 1), map.getValue());
                }
            }
        }
        return subMap;
    }

    protected void processPropertySource(PropertySource properties, boolean upperCaseUnderscoreSeperated) {
        synchronized (catalog) {
            for (String property : properties) {
                Object value = properties.get(property);
                if(upperCaseUnderscoreSeperated) {
                    property = property.toLowerCase(Locale.ENGLISH).replace('_', '.');
                }

                Map entries = resolveEntriesForKey(property, true);
                if(entries != null) {
                    entries.put(property, value);
                }
            }
        }
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

    protected Map<String,Object> resolveEntriesForKey(String name, boolean allowCreate) {
        Map<String,Object> entries = null;
        if(name.length() == 0) {
            return null;
        }
        char firstChar = name.charAt(0);
        if(Character.isLetter(firstChar)) {
            int index = ((int)firstChar) - 65;
            if(index < catalog.length && index > 0) {
                entries = catalog[index];
                if(allowCreate && entries == null) {
                    entries = new LinkedHashMap<>(5);
                    catalog[index] = entries;
                }
            }
        }
        return entries;
    }

}
