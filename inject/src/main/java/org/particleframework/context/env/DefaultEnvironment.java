package org.particleframework.context.env;

import org.particleframework.context.converters.StringArrayToClassArrayConverter;
import org.particleframework.context.converters.StringToClassConverter;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.io.scan.CachingClassPathAnnotationScanner;
import org.particleframework.core.io.scan.ClassPathAnnotationScanner;
import org.particleframework.core.io.service.SoftServiceLoader;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.core.util.CollectionUtils;
import org.particleframework.inject.BeanConfiguration;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.*;
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
        Set<String> specifiedNames = new HashSet<>(3);
        specifiedNames.addAll(CollectionUtils.setOf(names));
        EnvironmentsAndPackage environmentsAndPackage = deduceEnvironments();
        specifiedNames.addAll(environmentsAndPackage.enviroments);
        Package aPackage = environmentsAndPackage.aPackage;
        if(aPackage != null) {
            packages.add(aPackage.getName());
        }
        this.names = specifiedNames;
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
        synchronized (catalog) {
            for (int i = 0; i < catalog.length; i++) {
                catalog[i] = null;
            }
        }
        return this;
    }

    @Override
    public Map<String, Object> refreshAndDiff() {
        Map<String,Object>[] copiedCatalog = copyCatalog();
        refresh();
        return diffCatalog(copiedCatalog, catalog);
    }



    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context) {
        return conversionService.convert(object, targetType, context);
    }

    @Override
    public <S, T> boolean canConvert(Class<S> sourceType, Class<T> targetType) {
        return conversionService.canConvert(sourceType, targetType);
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
    private EnvironmentsAndPackage deduceEnvironments() {
        EnvironmentsAndPackage environmentsAndPackage = new EnvironmentsAndPackage();
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            String methodName = stackTraceElement.getMethodName();
            if(methodName.contains("$spock_")) {
                String className = stackTraceElement.getClassName();
                String packageName = NameUtils.getPackageName(className);
                environmentsAndPackage.aPackage = Package.getPackage(packageName);
                environmentsAndPackage.enviroments.add(Environment.TEST);
            }
            else if("main".equals(methodName)) {
                String packageName = NameUtils.getPackageName(stackTraceElement.getClassName());
                if(environmentsAndPackage.aPackage == null) {
                    environmentsAndPackage.aPackage = Package.getPackage(packageName);
                }
            }
            else {
                String className = stackTraceElement.getClassName();
                if(Stream.of("org.spockframework", "org.junit").anyMatch(className::startsWith)) {
                    environmentsAndPackage.enviroments.add(Environment.TEST);
                }
                else if(className.startsWith("com.android")) {
                    environmentsAndPackage.enviroments.add(Environment.ANDROID);
                }
            }
        }

        return environmentsAndPackage;
    }

    private Map<String, Object> diffCatalog(Map<String, Object>[] original, Map<String, Object>[] newCatalog) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (int i = 0; i < original.length; i++) {
            Map<String, Object> map = original[i];
            Map<String, Object> newMap = newCatalog[i];
            boolean hasNew = newMap != null;
            boolean hasOld = map != null;
            if(!hasOld && hasNew) {
                changes.putAll(newMap);
            }
            else {
                if(!hasNew && hasOld) {
                    changes.putAll(map);
                }
                else if(hasOld && hasNew) {
                    diffMap(map, newMap, changes);
                }
            }
        }
        return changes;
    }

    private void diffMap(Map<String, Object> map, Map<String, Object> newMap, Map<String, Object> changes) {
        for (Map.Entry<String, Object> entry : newMap.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            if(!map.containsKey(key)) {
                changes.put(key, newValue);
            }
            else {
                Object oldValue = map.get(key);
                boolean hasNew = newValue != null;
                boolean hasOld = oldValue != null;
                if(hasNew && !hasOld) {
                    changes.put(key, null);
                }
                else if(hasOld && !hasNew) {
                    changes.put(key, oldValue);
                }
                else if(hasNew && hasOld && !newValue.equals(oldValue)) {
                    changes.put(key, oldValue);
                }
            }
        }
    }

    private Map<String, Object>[] copyCatalog() {
        Map<String, Object>[] newCatalog = new Map[catalog.length];
        for (int i = 0; i < catalog.length; i++) {
            Map<String, Object> entry = catalog[i];
            if(entry != null) {
                newCatalog[i] = new LinkedHashMap<>(entry);
            }
        }
        return newCatalog;
    }



    private class EnvironmentsAndPackage {
        Package aPackage;
        Set<String> enviroments = new HashSet<>(1);
    }
}
