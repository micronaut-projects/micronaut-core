/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.env;

import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.PropertyResolverDelegate;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.AnnotationScanner;
import io.micronaut.core.io.scan.BeanIntrospectionScanner;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * <p>The default implementation of the {@link Environment} interface. Configures a named environment.</p>
 *
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
@Internal
final class DefaultEnvironment implements Environment, PropertyResolverDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultEnvironment.class);
    private static final String FILE_SEPARATOR = ",";
    private static final List<String> DEFAULT_CONFIG_LOCATIONS = Arrays.asList("classpath:/", "file:config/");
    private static final String ENV_PROPERTY_SOURCES_KEY = "MICRONAUT_CONFIG_FILES";
    private static final List<PropertySourceLoader> DEFAULT_PROPERTY_SOURCE_LOADERS = List.of(
        new YamlPropertySourceLoader(),
        new PropertiesPropertySourceLoader(),
        new ConstantPropertySourceLoader()
    );

    private final ClassPathResourceLoader resourceLoader;
    private final MutableConversionService mutableConversionService;
    private final String applicationName;
    private final Set<String> names;
    private final ClassLoader classLoader;
    private final Collection<String> packages = Collections.synchronizedSet(CollectionUtils.newLinkedHashSet(10));
    private final AnnotationScanner annotationScanner;
    private final Collection<String> configurationIncludes = new HashSet<>(3);
    private final Collection<String> configurationExcludes = new HashSet<>(3);
    private Collection<PropertySourceLoader> propertySourceLoaderList;
    private final Map<String, PropertySourceLoader> loaderByFormatMap = Collections.synchronizedMap(CollectionUtils.newLinkedHashMap(10));
    private final Map<String, Boolean> presenceCache = new ConcurrentHashMap<>();
    private final ApplicationContextConfiguration configuration;
    private final Collection<String> configLocations;
    private final PropertySourcePropertyResolver propertyPlaceholderResolver;
    private final PropertyResolver propertyResolver;

    private final List<PropertySource> refreshablePropertySources = new ArrayList<>(10);
    private final Map<String, PropertySource> propertySources = Collections.synchronizedMap(CollectionUtils.newLinkedHashMap(10));

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean reading = new AtomicBoolean(false);

    // For configuration maps, sets with low access - it's enough to use Collections.synchronized

    /**
     * Construct a new environment for the given configuration.
     *
     * @param configuration The configuration
     */
    public DefaultEnvironment(@NonNull ApplicationContextConfiguration configuration) {
        MutableConversionService conversionService = configuration.getConversionService().orElseGet(MutableConversionService::create);
        this.propertyPlaceholderResolver = new PropertySourcePropertyResolver(
            conversionService,
            true
        );
        this.applicationName = configuration.getApplicationName();
        if (applicationName.isBlank()) {
            throw new ConfigurationException("Application name cannot be blank");
        }
        this.propertyResolver = propertyPlaceholderResolver;
        this.mutableConversionService = conversionService;
        this.configuration = configuration;
        this.resourceLoader = configuration.getResourceLoader();
        this.classLoader = configuration.getClassLoader();
        this.annotationScanner = new BeanIntrospectionScanner();
        DefaultEnvironmentAndPackageDeducer deducer = new DefaultEnvironmentAndPackageDeducer(LOG, configuration);
        EnvironmentNamesDeducer environmentNamesDeducer = configuration.getEnvironmentNamesDeducer();
        if (environmentNamesDeducer == null) {
            environmentNamesDeducer = deducer;
        }
        EnvironmentPackagesDeducer packagesDeducer = configuration.getPackageDeducer();
        if (packagesDeducer == null) {
            packagesDeducer = deducer;
        }
        this.names = environmentNamesDeducer.deduceEnvironmentNames();
        if (!names.isEmpty()) {
            LOG.info("Established active environments: {}", names);
        }
        this.packages.addAll(packagesDeducer.deducePackages());
        List<String> configLocations = configuration.getOverrideConfigLocations() == null
            ? new ArrayList<>(DEFAULT_CONFIG_LOCATIONS)
            : new ArrayList<>(configuration.getOverrideConfigLocations());
        // Search config locations in reverse order
        Collections.reverse(configLocations);
        this.configLocations = configLocations;
    }

    @Override
    public PropertyResolver delegate() {
        return propertyResolver;
    }

    @Override
    public boolean isPresent(String className) {
        return presenceCache.computeIfAbsent(className, s -> ClassUtils.isPresent(className, classLoader));
    }

    @Override
    public PropertyPlaceholderResolver getPlaceholderResolver() {
        return propertyPlaceholderResolver.propertyPlaceholderResolver;
    }

    @Override
    public Stream<Class<?>> scan(Class<? extends Annotation> annotation) {
        return annotationScanner.scan(annotation, getPackages());
    }

    @Override
    public Stream<Class<?>> scan(Class<? extends Annotation> annotation, String... packages) {
        return annotationScanner.scan(annotation, packages);
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
        propertySources.put(propertySource.getName(), propertySource);
        if (isRunning() && !reading.get()) {
            propertyPlaceholderResolver.resetCaches();
            process(propertySource);
        }
        return this;
    }

    @Override
    public Environment removePropertySource(PropertySource propertySource) {
        propertySources.remove(propertySource.getName());
        if (isRunning() && !reading.get()) {
            propertyPlaceholderResolver.resetCaches();
        }
        return this;
    }

    @Override
    public Environment addPackage(String pkg) {
        if (!this.packages.contains(pkg)) {
            this.packages.add(pkg);
        }
        return this;
    }

    @Override
    public Environment addConfigurationExcludes(@Nullable String... names) {
        if (names != null) {
            configurationExcludes.addAll(Arrays.asList(names));
        }
        return this;
    }

    @Override
    public Environment addConfigurationIncludes(String... names) {
        if (names != null) {
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
    public Collection<PropertySource> getPropertySources() {
        return Collections.unmodifiableCollection(propertySources.values());
    }

    @Override
    public Environment start() {
        if (running.compareAndSet(false, true)) {
            LOG.debug("Starting environment {} for active names {}", this, getActiveNames());
            readProperties();
        }
        return this;
    }

    private void readProperties() {
        if (reading.compareAndSet(false, true)) {
            loadProperties();
            reading.set(false);
        }
    }

    private void dropProperties() {
        propertySources.values().removeAll(refreshablePropertySources);
        propertyPlaceholderResolver.reset();
    }

    private void refreshProperties() {
        dropProperties();
        readProperties();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Environment stop() {
        running.set(false);
        reading.set(false);
        dropProperties();
        if (mutableConversionService instanceof DefaultMutableConversionService defaultMutableConversionService) {
            defaultMutableConversionService.reset();
        }
        return this;
    }

    @Override
    public Map<String, Object> refreshAndDiff() {
        return propertyPlaceholderResolver.diff(this::refresh);
    }

    @Override
    public Environment refresh() {
        refreshProperties();
        return this;
    }

    @Override
    public Optional<InputStream> getResourceAsStream(String path) {
        return resourceLoader.getResourceAsStream(path);
    }

    @Override
    public Optional<URL> getResource(String path) {
        return resourceLoader.getResource(path);
    }

    @Override
    public Stream<URL> getResources(String path) {
        return resourceLoader.getResources(path);
    }

    @Override
    public boolean supportsPrefix(String path) {
        return resourceLoader.supportsPrefix(path);
    }

    @Override
    public ResourceLoader forBase(String basePath) {
        return resourceLoader.forBase(basePath);
    }

    private void loadProperties() {
        refreshablePropertySources.clear();
        List<PropertySource> propertySources;
        if (configuration.isEnableDefaultPropertySources()) {
            propertySources = readPropertySourceList(applicationName);
            addDefaultPropertySources(propertySources);
            String propertySourcesSystemProperty = CachedEnvironment.getProperty(Environment.PROPERTY_SOURCES_KEY);
            if (propertySourcesSystemProperty != null) {
                propertySources.addAll(readPropertySourceListFromFiles(propertySourcesSystemProperty, PropertySource.Origin.of(Environment.PROPERTY_SOURCES_KEY)));
            }
            String propertySourcesEnv = CachedEnvironment.getenv(ENV_PROPERTY_SOURCES_KEY);
            if (propertySourcesEnv != null) {
                propertySources.addAll(readPropertySourceListFromFiles(propertySourcesEnv, PropertySource.Origin.of(ENV_PROPERTY_SOURCES_KEY)));
            }
            refreshablePropertySources.addAll(propertySources);

        } else {
            propertySources = new ArrayList<>(this.propertySources.size());
        }
        propertySources.addAll(this.propertySources.values());
        for (PropertySourcesLocator propertySourcesLocator : configuration.getPropertySourcesLocators()) {
            propertySources.addAll(propertySourcesLocator.load(this));
        }

        OrderUtil.sortOrdered(propertySources);

        for (PropertySource propertySource : propertySources) {
            process(propertySource);
        }
    }

    private void process(PropertySource propertySource) {
        LOG.debug("Processing property source: {} convention: {}", propertySource.getName(), propertySource.getConvention());
        propertySources.put(propertySource.getName(), propertySource);
        propertyPlaceholderResolver.processPropertySource(propertySource, propertySource.getConvention());
    }

    /**
     * Resolve the property sources for files passed via system property and system env.
     *
     * @param files The comma separated list of files
     * @param origin The origin of the property sources
     * @return The list of property sources for each file
     */
    private List<PropertySource> readPropertySourceListFromFiles(@Nullable String files, PropertySource.Origin origin) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<String> list = List.of(files.split(FILE_SEPARATOR));
        if (list.isEmpty()) {
            return List.of();
        }
        int order = AbstractPropertySourceLoader.DEFAULT_POSITION + 50;
        List<PropertySource> propertySources = new ArrayList<>(list.size());
        for (String filePath : list) {
            String extension = NameUtils.extension(filePath);
            String fileName = NameUtils.filename(filePath);
            PropertySourceLoader propertySourceLoader = loaderByFormatMap.get(extension);
            if (propertySourceLoader != null) {
                LOG.debug("Reading property sources from loader: {}", propertySourceLoader);
                Optional<Map<String, Object>> properties = readPropertiesFromLoader(fileName, filePath, propertySourceLoader);
                if (properties.isPresent()) {
                    propertySources.add(PropertySource.of(filePath, properties.get(), order));
                }
                order++;
            } else {
                throw new ConfigurationException("Unsupported properties file format while reading " + fileName + "." + extension + " from " + filePath);
            }
        }
        return propertySources;
    }

    /**
     * @param name The name to resolver property sources
     * @return The list of property sources
     */
    private List<PropertySource> readPropertySourceList(String name) {
        List<PropertySource> propertySources = new ArrayList<>(configLocations.size());
        for (String configLocation : configLocations) {
            ResourceLoader resourceLoader;
            if (configLocation.equals("classpath:/")) {
                resourceLoader = this;
            } else if (configLocation.startsWith("classpath:")) {
                resourceLoader = this.forBase(configLocation);
            } else  if (configLocation.startsWith("file:")) {
                configLocation = configLocation.substring(5);
                Path configLocationPath = Paths.get(configLocation);
                if (Files.exists(configLocationPath) && Files.isDirectory(configLocationPath) && Files.isReadable(configLocationPath)) {
                    resourceLoader = new DefaultFileSystemResourceLoader(configLocationPath);
                } else {
                    continue; // Skip not existing config location
                }
            } else {
                throw new ConfigurationException("Unsupported config location format: " + configLocation);
            }
            readPropertySourceList(name, resourceLoader, propertySources);
        }
        return propertySources;
    }

    private void readPropertySourceList(String name, ResourceLoader resourceLoader, List<PropertySource> propertySources) {
        Collection<PropertySourceLoader> propertySourceLoaders = getPropertySourceLoaders();
        if (propertySourceLoaders.isEmpty()) {
            loadPropertySourceFromLoader(name, new PropertiesPropertySourceLoader(), propertySources, resourceLoader);
        } else {
            for (PropertySourceLoader propertySourceLoader : propertySourceLoaders) {
                LOG.debug("Reading property sources from loader: {}", propertySourceLoader);
                loadPropertySourceFromLoader(name, propertySourceLoader, propertySources, resourceLoader);
            }
        }
    }

    /**
     * Adds default property sources.
     *
     * @param propertySources The list of property sources
     */
    private void addDefaultPropertySources(List<PropertySource> propertySources) {
        if (!this.propertySources.containsKey(SystemPropertiesPropertySource.NAME)) {
            propertySources.add(new SystemPropertiesPropertySource());
        }
        if (configuration.isEnvironmentPropertySource() && !this.propertySources.containsKey(EnvironmentPropertySource.NAME)) {
            List<String> includes = configuration.getEnvironmentVariableIncludes();
            List<String> excludes = configuration.getEnvironmentVariableExcludes();
            if (this.names.contains(Environment.KUBERNETES)) {
                propertySources.add(new KubernetesEnvironmentPropertySource(includes, excludes));
            } else {
                propertySources.add(new EnvironmentPropertySource(includes, excludes));
            }
        }
    }

    @Override
    public Optional<PropertyEntry> getPropertyEntry(String name) {
        for (PropertyCatalog propertyCatalog : PropertyCatalog.values()) {
            Map<String, DefaultPropertyEntry> entries = propertyPlaceholderResolver.resolveEntriesForKey(name, false, propertyCatalog);
            if (entries != null) {
                DefaultPropertyEntry entry = entries.get(name);
                if (entry != null) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Obtains the {@link PropertySourceLoader} instances.
     *
     * @return A collection of {@link PropertySourceLoader}
     */
    @Override
    public Collection<PropertySourceLoader> getPropertySourceLoaders() {
        Collection<PropertySourceLoader> propertySourceLoaderList = this.propertySourceLoaderList;
        if (propertySourceLoaderList == null) {
            synchronized (this) { // double check
                propertySourceLoaderList = this.propertySourceLoaderList;
                if (propertySourceLoaderList == null) {
                    propertySourceLoaderList = evaluatePropertySourceLoaders();
                    this.propertySourceLoaderList = propertySourceLoaderList;
                }
            }
        }
        return propertySourceLoaderList;
    }

    private Collection<PropertySourceLoader> evaluatePropertySourceLoaders() {
        SoftServiceLoader<PropertySourceLoader> definitions = SoftServiceLoader.load(PropertySourceLoader.class, getClassLoader());
        List<PropertySourceLoader> propertySourceLoaders = definitions.collectAll();
        Collection<PropertySourceLoader> allLoaders = CollectionUtils.concat(DEFAULT_PROPERTY_SOURCE_LOADERS, propertySourceLoaders);
        for (Iterator<PropertySourceLoader> iterator = allLoaders.iterator(); iterator.hasNext(); ) {
            PropertySourceLoader propertySourceLoader = iterator.next();
            if (!propertySourceLoader.isEnabled()) {
                iterator.remove();
                continue;
            }
            Set<String> extensions = propertySourceLoader.getExtensions();
            for (String extension : extensions) {
                loaderByFormatMap.put(extension, propertySourceLoader);
            }
        }
        return allLoaders;
    }

    private void loadPropertySourceFromLoader(String name, PropertySourceLoader propertySourceLoader, List<PropertySource> propertySources, ResourceLoader resourceLoader) {
        Optional<PropertySource> defaultPropertySource = propertySourceLoader.load(name, resourceLoader);
        defaultPropertySource.ifPresent(propertySources::add);
        Set<String> activeNames = getActiveNames();
        int i = 0;
        for (String activeName: activeNames) {
            Optional<PropertySource> propertySource = propertySourceLoader.loadEnv(name, resourceLoader, ActiveEnvironment.of(activeName, i));
            propertySource.ifPresent(propertySources::add);
            i++;
        }
    }

    /**
     * Read the property source.
     *
     * @param fileName             Name of the file to be used as property source name
     * @param filePath             Absolute file path
     * @param propertySourceLoader The appropriate property source loader
     * @throws ConfigurationException If unable to find the appropriate property source loader for the given file
     */
    private Optional<Map<String, Object>> readPropertiesFromLoader(String fileName, String filePath, PropertySourceLoader propertySourceLoader) throws ConfigurationException {
        ResourceLoader loader = new ResourceResolver().getSupportingLoader(filePath)
                .orElse(FileSystemResourceLoader.defaultLoader());
        try {
            Optional<InputStream> inputStream = loader.getResourceAsStream(filePath);
            if (inputStream.isPresent()) {
                return Optional.of(propertySourceLoader.read(fileName, inputStream.get()));
            } else {
                throw new ConfigurationException("Failed to read configuration file: " + filePath);
            }
        } catch (IOException e) {
            throw new ConfigurationException("Unsupported properties file: " + fileName);
        }
    }

    @Override
    public void close() {
        try {
            propertyPlaceholderResolver.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close property placeholder resolver: " + e.getMessage(), e);
        }
        for (PropertySourcesLocator propertySourcesLocator : configuration.getPropertySourcesLocators()) {
            if (propertySourcesLocator instanceof Closeable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to close property sources locator " + propertySourcesLocator.getClass() + ": " + e.getMessage(), e);
                }
            }
        }
        stop();
    }

    @Override
    public MutableConversionService getConversionService() {
        return mutableConversionService;
    }
}
