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
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.io.ResourceConflictException;
import io.micronaut.core.io.ResourceDuplicateException;
import io.micronaut.core.io.ResourceLoadStrategy;
import io.micronaut.core.io.ResourceLoadStrategyType;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
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
    private static final String FILE_PREFIX = "file:";

    private final ClassPathResourceLoader resourceLoader;
    private final MutableConversionService mutableConversionService;
    private final String applicationName;
    private final Set<String> names;
    private final ClassLoader classLoader;
    private final Collection<String> packages = Collections.synchronizedSet(CollectionUtils.newLinkedHashSet(10));
    private final AnnotationScanner annotationScanner;
    private final Collection<String> configurationIncludes = new HashSet<>(3);
    private final Collection<String> configurationExcludes = new HashSet<>(3);
    @Nullable
    private Collection<PropertySourceLoader> propertySourceLoaderList;
    private final Map<String, PropertySourceLoader> loaderByFormatMap = Collections.synchronizedMap(CollectionUtils.newLinkedHashMap(10));
    private final Map<String, Boolean> presenceCache = new ConcurrentHashMap<>();
    private final ApplicationContextConfiguration configuration;
    private final Collection<String> configLocations;
    private final PropertySourcePropertyResolver propertyPlaceholderResolver;
    private final PropertyResolver propertyResolver;

    private final List<PropertySource> refreshablePropertySources = new ArrayList<>(10);
    private final Map<String, PropertySource> propertySources = Collections.synchronizedMap(CollectionUtils.newLinkedHashMap(10));
    private final Collection<PropertySourcesLocator> propertySourcesLocators;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean reading = new AtomicBoolean(false);

    // For configuration maps, sets with low access - it's enough to use Collections.synchronized

    /**
     * Construct a new environment for the given configuration.
     *
     * @param configuration The configuration
     */
    public DefaultEnvironment(ApplicationContextConfiguration configuration) {
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
        this.propertySourcesLocators = configuration.getPropertySourcesLocators();
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
        internalAddPropertySource(propertySource);
        if (isRunning() && !reading.get()) {
            propertyPlaceholderResolver.resetCaches();
            internalProcessPropertySource(propertySource);
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
        for (PropertySource refreshable : refreshablePropertySources) {
            propertySources.remove(refreshable.getName());
        }
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
        List<PropertySource> propertySources = new ArrayList<>(configLocations.size());
        Set<String> preExistingSourceNames = new LinkedHashSet<>(this.propertySources.keySet());
        if (configuration.isEnableDefaultPropertySources()) {
            propertySources.addAll(readPropertySourceList(applicationName));
            List<PropertySource> defaultPropertySources = new ArrayList<>(4);
            addDefaultPropertySources(defaultPropertySources);
            propertySources.addAll(defaultPropertySources);
            String propertySourcesSystemProperty = CachedEnvironment.getProperty(Environment.PROPERTY_SOURCES_KEY);
            if (propertySourcesSystemProperty != null) {
                propertySources.addAll(readPropertySourceListFromFiles(propertySourcesSystemProperty));
            }
            String propertySourcesEnv = CachedEnvironment.getenv(ENV_PROPERTY_SOURCES_KEY);
            if (propertySourcesEnv != null) {
                propertySources.addAll(readPropertySourceListFromFiles(propertySourcesEnv));
            }

        } else {
            propertySources = new ArrayList<>(this.propertySources.size());
        }
        for (PropertySource propertySource : this.propertySources.values()) {
            if (!propertySources.contains(propertySource)) {
                propertySources.add(propertySource);
            }
        }

        for (PropertySource propertySource : propertySources) {
            internalAddPropertySource(propertySource);
        }

        if (!propertySourcesLocators.isEmpty()) {
            for (PropertySourcesLocator propertySourcesLocator : propertySourcesLocators) {
                boolean isBootstrapLocator = propertySourcesLocator instanceof BootstrapLocatorMarker;
                Collection<PropertySource> sources = propertySourcesLocator.load(this);
                propertySources.addAll(sources);
                if (isBootstrapLocator) {
                    for (PropertySource source : sources) {
                        internalAddPropertySource(source);
                    }
                }
            }
        }

        for (PropertySource propertySource : propertySources) {
            if (!PropertySource.CONTEXT.equals(propertySource.getName()) && !preExistingSourceNames.contains(propertySource.getName())) {
                refreshablePropertySources.add(propertySource);
            }
        }

        OrderUtil.sortOrdered(propertySources);
        for (PropertySource propertySource : propertySources) {
            internalProcessPropertySource(propertySource);
        }
    }

    private void internalAddPropertySource(PropertySource propertySource) {
        propertySources.put(propertySource.getName(), propertySource);
    }

    private void internalProcessPropertySource(PropertySource propertySource) {
        LOG.debug("Processing property source: {} convention: {}", propertySource.getName(), propertySource.getConvention());
        propertyPlaceholderResolver.processPropertySource(propertySource, propertySource.getConvention());
    }

    /**
     * Resolve the property sources for files passed via system property and system env.
     *
     * @param files The comma separated list of files
     * @return The list of property sources for each file
     */
    private List<PropertySource> readPropertySourceListFromFiles(@Nullable String files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<String> list = List.of(files.split(FILE_SEPARATOR));
        if (list.isEmpty()) {
            return List.of();
        }
        getPropertySourceLoaders();
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
                    PropertySource.Origin sourceOrigin;
                    if (filePath.startsWith("classpath:") || filePath.startsWith(FILE_PREFIX)) {
                        sourceOrigin = PropertySource.Origin.of(filePath);
                    } else {
                        sourceOrigin = PropertySource.Origin.of(FILE_PREFIX + filePath);
                    }
                    propertySources.add(PropertySource.of(filePath, properties.get(), sourceOrigin, order));
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
            } else  if (configLocation.startsWith(FILE_PREFIX)) {
                configLocation = configLocation.substring(FILE_PREFIX.length());
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
        Collection<PropertySourceLoader> currentPropertySourceLoaders = this.propertySourceLoaderList;
        if (currentPropertySourceLoaders == null) {
            synchronized (this) { // double check
                currentPropertySourceLoaders = this.propertySourceLoaderList;
                if (currentPropertySourceLoaders == null) {
                    currentPropertySourceLoaders = evaluatePropertySourceLoaders();
                    this.propertySourceLoaderList = currentPropertySourceLoaders;
                }
            }
        }
        return currentPropertySourceLoaders;
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

    Optional<PropertySource> loadImportedPropertySource(ResourceLoader resourceLoader,
                                                        String resourcePath,
                                                        String sourceName,
                                                        PropertySource.Origin origin) {
        String extension = NameUtils.extension(resourcePath);
        String resourceName = NameUtils.filename(resourcePath);
        if (!extension.isEmpty()) {
            getPropertySourceLoaders();
            PropertySourceLoader propertySourceLoader = loaderByFormatMap.get(extension);
            if (propertySourceLoader == null) {
                throw new ConfigurationException("Unsupported properties file format while reading " + resourceName + " from " + resourcePath);
            }
            return readImportedPropertySource(resourceLoader, resourcePath, sourceName, origin, propertySourceLoader);
        }

        Collection<PropertySourceLoader> loaders = getPropertySourceLoaders();
        if (loaders.isEmpty()) {
            loaders = List.of(new PropertiesPropertySourceLoader());
        }
        for (PropertySourceLoader propertySourceLoader : loaders) {
            Set<String> extensions = propertySourceLoader.getExtensions();
            for (String candidateExtension : extensions) {
                String candidatePath = resourcePath + "." + candidateExtension;
                Optional<PropertySource> imported = readImportedPropertySource(resourceLoader, candidatePath, sourceName, origin, propertySourceLoader);
                if (imported.isPresent()) {
                    return imported;
                }
            }
        }
        return Optional.empty();
    }

    Optional<PropertySource> loadImportedClasspathPropertySource(String resourcePath,
                                                                 String sourceName,
                                                                 PropertySource.Origin origin,
                                                                 boolean allowMultiple) {
        String extension = NameUtils.extension(resourcePath);
        String resourceName = NameUtils.filename(resourcePath);
        if (!extension.isEmpty()) {
            getPropertySourceLoaders();
            PropertySourceLoader propertySourceLoader = loaderByFormatMap.get(extension);
            if (propertySourceLoader == null) {
                throw new ConfigurationException("Unsupported properties file format while reading " + resourceName + " from " + resourcePath);
            }
            return readImportedClasspathPropertySource(resourcePath, sourceName, origin, propertySourceLoader, allowMultiple);
        }

        Collection<PropertySourceLoader> loaders = getPropertySourceLoaders();
        if (loaders.isEmpty()) {
            loaders = List.of(new PropertiesPropertySourceLoader());
        }
        for (PropertySourceLoader propertySourceLoader : loaders) {
            Set<String> extensions = propertySourceLoader.getExtensions();
            for (String candidateExtension : extensions) {
                String candidatePath = resourcePath + "." + candidateExtension;
                Optional<PropertySource> imported = readImportedClasspathPropertySource(candidatePath, sourceName, origin, propertySourceLoader, allowMultiple);
                if (imported.isPresent()) {
                    return imported;
                }
            }
        }
        return Optional.empty();
    }

    Optional<PropertySource> loadImportedPropertySourceFromContent(String content,
                                                                   String sourceName,
                                                                   String extension,
                                                                   PropertySource.Origin origin) {
        getPropertySourceLoaders();
        PropertySourceLoader propertySourceLoader = loaderByFormatMap.get(extension);
        if (propertySourceLoader == null) {
            if (!extension.isEmpty()) {
                throw new ConfigurationException("Unsupported properties content format while reading " + sourceName + "." + extension);
            }
            return Optional.empty();
        }
        try (InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            Map<String, Object> values = propertySourceLoader.read(sourceName, in);
            if (values.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(PropertySource.of(sourceName, values, origin));
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read properties content for source: " + sourceName + "." + extension, e);
        }
    }

    private Optional<PropertySource> readImportedPropertySource(ResourceLoader resourceLoader,
                                                                String resourcePath,
                                                                String sourceName,
                                                                PropertySource.Origin origin,
                                                                PropertySourceLoader propertySourceLoader) {
        try {
            Optional<InputStream> inputStream = resourceLoader.getResourceAsStream(resourcePath);
            if (inputStream.isEmpty()) {
                return Optional.empty();
            }
            try (InputStream in = inputStream.get()) {
                Map<String, Object> values = propertySourceLoader.read(sourceName, in);
                if (values.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(PropertySource.of(sourceName, values, origin));
            }
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read imported config: " + resourcePath, e);
        }
    }

    private Optional<PropertySource> readImportedClasspathPropertySource(String resourcePath,
                                                                         String sourceName,
                                                                         PropertySource.Origin origin,
                                                                         PropertySourceLoader propertySourceLoader,
                                                                         boolean allowMultiple) {
        ResourceLoadStrategy strategy = ResourceLoadStrategy.builder()
            .type(allowMultiple ? ResourceLoadStrategyType.MERGE_ALL : ResourceLoadStrategyType.FAIL_ON_DUPLICATE)
            .build();
        List<URL> resources;
        try {
            resources = ClassPathResourceLoader.resolveResources(this, resourcePath, strategy);
        } catch (ResourceDuplicateException e) {
            throw new ConfigurationException(buildDuplicateClasspathImportMessage(resourcePath, e.getResources()));
        } catch (ResourceConflictException e) {
            if (!allowMultiple) {
                throw new ConfigurationException(buildDuplicateClasspathImportMessage(resourcePath, e.getResources()));
            }
            resources = e.getResources();
        }
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        if (!allowMultiple && resources.size() > 1) {
            throw new ConfigurationException(buildDuplicateClasspathImportMessage(resourcePath, resources));
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (URL resource : resources) {
            try (InputStream in = resource.openStream()) {
                values.putAll(propertySourceLoader.read(sourceName, in));
            } catch (IOException e) {
                throw new ConfigurationException("Failed to read imported config: " + resourcePath, e);
            }
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PropertySource.of(sourceName, values, origin));
    }

    private static String buildDuplicateClasspathImportMessage(String resourcePath, List<URL> resources) {
        StringBuilder message = new StringBuilder("Multiple classpath resources found for import '")
            .append(resourcePath)
            .append("'. Use classpath*:// to load all matches.\nFound:");
        for (URL resource : resources) {
            message.append("\n - ").append(resource.toExternalForm());
        }
        return message.toString();
    }

    private void loadPropertySourceFromLoader(String name, PropertySourceLoader propertySourceLoader, List<PropertySource> propertySources, ResourceLoader resourceLoader) {
        ResourceLoadStrategy strategy = configuration.getConfigurationLoadingStrategy();

        Optional<PropertySource> defaultPropertySource = loadPropertySourceFromLoader(name, propertySourceLoader, resourceLoader, strategy);
        defaultPropertySource.ifPresent(propertySources::add);

        Set<String> activeNames = getActiveNames();
        int i = 0;
        for (String activeName : activeNames) {
            ActiveEnvironment activeEnvironment = ActiveEnvironment.of(activeName, i);
            Optional<PropertySource> propertySource = loadPropertySourceFromLoader(name, propertySourceLoader, resourceLoader, activeEnvironment, strategy);
            propertySource.ifPresent(propertySources::add);
            i++;
        }
    }

    private Optional<PropertySource> loadPropertySourceFromLoader(String name,
                                                                   PropertySourceLoader propertySourceLoader,
                                                                   ResourceLoader resourceLoader,
                                                                   ResourceLoadStrategy strategy) {
        if (propertySourceLoader instanceof AbstractPropertySourceLoader abstractPropertySourceLoader) {
            return loadPropertySourceFromAbstractLoader(name, abstractPropertySourceLoader, resourceLoader, abstractPropertySourceLoader.getOrder(), strategy);
        }
        return propertySourceLoader.load(name, resourceLoader);
    }

    private Optional<PropertySource> loadPropertySourceFromLoader(String name,
                                                                   PropertySourceLoader propertySourceLoader,
                                                                   ResourceLoader resourceLoader,
                                                                   ActiveEnvironment activeEnvironment,
                                                                   ResourceLoadStrategy strategy) {
        if (propertySourceLoader instanceof AbstractPropertySourceLoader abstractPropertySourceLoader) {
            String envName = name + "-" + activeEnvironment.getName();
            int order = abstractPropertySourceLoader.getOrder() + 1 + activeEnvironment.getPriority();
            return loadPropertySourceFromAbstractLoader(envName, abstractPropertySourceLoader, resourceLoader, order, strategy);
        }
        return propertySourceLoader.loadEnv(name, resourceLoader, activeEnvironment);
    }

    private Optional<PropertySource> loadPropertySourceFromAbstractLoader(String fileName,
                                                                          AbstractPropertySourceLoader propertySourceLoader,
                                                                          ResourceLoader resourceLoader,
                                                                          int order,
                                                                          ResourceLoadStrategy strategy) {
        if (!propertySourceLoader.isEnabled()) {
            return Optional.empty();
        }

        final boolean needsAllResources = strategy.type() == ResourceLoadStrategyType.MERGE_ALL
            || strategy.type() == ResourceLoadStrategyType.FAIL_ON_DUPLICATE
            || (strategy.type() == ResourceLoadStrategyType.FIRST_MATCH && strategy.warnOnDuplicates());

        String[] extensions = propertySourceLoader.getExtensions().toArray(new String[0]);
        if (!needsAllResources) {
            // FIRST_MATCH with warnOnDuplicates=false: use first resource without enumerating all
            for (String ext : extensions) {
                String fileExt = fileName + "." + ext;
                Optional<InputStream> config = propertySourceLoader.readInput(resourceLoader, fileExt);
                if (config.isPresent()) {
                    try (InputStream input = config.get()) {
                        Map<String, Object> merged = propertySourceLoader.read(fileName, input);
                        if (!merged.isEmpty()) {
                            return Optional.of(propertySourceLoader.createPropertySource(fileName, merged, order, PropertySource.Origin.of(fileExt)));
                        }
                    } catch (IOException e) {
                        throw new ConfigurationException("I/O exception occurred reading [" + fileExt + "]: " + e.getMessage(), e);
                    }
                }
            }
            return Optional.empty();
        }

        Map<String, List<URL>> extensionResources = new LinkedHashMap<>(extensions.length);
        Set<String> mergeExtensions = new HashSet<>();
        int extensionsWithResources = 0;

        for (String ext : extensions) {
            String fileExt = fileName + "." + ext;
            List<URL> urls;
            try {
                urls = ClassPathResourceLoader.resolveResources(resourceLoader, fileExt, strategy);
            } catch (ResourceDuplicateException e) {
                throw new ConfigurationException(buildDuplicateConfigurationMessage(e.getResourceName(), e.getResources()));
            } catch (ResourceConflictException e) {
                urls = e.getResources();
                mergeExtensions.add(ext);
            }
            extensionResources.put(ext, urls);
            if (!urls.isEmpty()) {
                extensionsWithResources++;
            }
        }

        if (extensionsWithResources > 1 && strategy.type() != ResourceLoadStrategyType.MERGE_ALL) {
            List<URL> allUrls = new ArrayList<>();
            for (List<URL> urls : extensionResources.values()) {
                allUrls.addAll(urls);
            }
            if (!allUrls.isEmpty() && strategy.type() == ResourceLoadStrategyType.FAIL_ON_DUPLICATE) {
                throw new ConfigurationException(buildDuplicateConfigurationMessage(fileName, allUrls));
            }
        }

        for (String ext : extensions) {
            String fileExt = fileName + "." + ext;
            List<URL> urls = Objects.requireNonNullElse(extensionResources.get(ext), Collections.emptyList());
            Map<String, Object> merged = Collections.emptyMap();

            if (urls.isEmpty()) {
                Optional<InputStream> config = propertySourceLoader.readInput(resourceLoader, fileExt);
                if (config.isPresent()) {
                    try (InputStream input = config.get()) {
                        merged = propertySourceLoader.read(fileName, input);
                    } catch (IOException e) {
                        throw new ConfigurationException("I/O exception occurred reading [" + fileExt + "]: " + e.getMessage(), e);
                    }
                }
            } else if (mergeExtensions.contains(ext) && urls.size() > 1) {
                List<URL> orderedUrls = urls;
                if (!strategy.mergeOrder().isEmpty()) {
                    orderedUrls = orderByArtifactPatterns(urls, strategy.mergeOrder());
                }

                if (LOG.isInfoEnabled()) {
                    LOG.info("Merging configuration resources '{}' in order: {}", fileExt, orderedUrls);
                }

                Map<String, Object> mergedMap = new LinkedHashMap<>(64);
                for (URL url : orderedUrls) {
                    try (InputStream input = url.openStream()) {
                        mergedMap.putAll(propertySourceLoader.read(fileName, input));
                    } catch (IOException e) {
                        throw new ConfigurationException("I/O exception occurred reading [" + fileExt + "] from [" + url + "]: " + e.getMessage(), e);
                    }
                }
                merged = mergedMap;
            } else {
                Optional<InputStream> config = propertySourceLoader.readInput(resourceLoader, fileExt);
                if (config.isPresent()) {
                    try (InputStream input = config.get()) {
                        merged = propertySourceLoader.read(fileName, input);
                    } catch (IOException e) {
                        throw new ConfigurationException("I/O exception occurred reading [" + fileExt + "]: " + e.getMessage(), e);
                    }
                }
            }

            if (!merged.isEmpty()) {
                return Optional.of(propertySourceLoader.createPropertySource(fileName, merged, order, PropertySource.Origin.of(fileExt)));
            }
        }

        return Optional.empty();
    }

    private static String buildDuplicateConfigurationMessage(String resourceName, List<URL> urls) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("Duplicate configuration resource '").append(resourceName).append("' found on the classpath:");
        for (URL url : urls) {
            sb.append("\n - ").append(url.toExternalForm());
        }
        return sb.toString();
    }

    private static List<URL> orderByArtifactPatterns(List<URL> urls, List<String> artifactPatterns) {
        final List<Pattern> patterns = new ArrayList<>(artifactPatterns.size());
        for (String p : artifactPatterns) {
            try {
                patterns.add(Pattern.compile(p));
            } catch (Exception e) {
                throw new ConfigurationException("Invalid mergeOrder regex pattern: " + p, e);
            }
        }

        record IndexedUrl(int originalIndex, int patternIndex, URL url) {
        }

        List<IndexedUrl> indexed = new ArrayList<>(urls.size());
        for (int i = 0; i < urls.size(); i++) {
            URL url = urls.get(i);
            String artifactName = artifactName(url);
            int index = patterns.size();
            for (int p = 0; p < patterns.size(); p++) {
                if (patterns.get(p).matcher(artifactName).matches()) {
                    index = p;
                    break;
                }
            }
            indexed.add(new IndexedUrl(i, index, url));
        }

        indexed.sort(Comparator
            .comparingInt(IndexedUrl::patternIndex)
            .thenComparingInt(IndexedUrl::originalIndex));

        List<URL> ordered = new ArrayList<>(urls.size());
        for (IndexedUrl idx : indexed) {
            ordered.add(idx.url());
        }
        return ordered;
    }

    private static String artifactName(URL url) {
        String external = url.toExternalForm();

        int bangIndex = external.indexOf("!/");
        String withoutEntry = bangIndex > -1 ? external.substring(0, bangIndex) : external;
        if (withoutEntry.startsWith("jar:")) {
            withoutEntry = withoutEntry.substring(4);
        }
        if (withoutEntry.startsWith(FILE_PREFIX)) {
            withoutEntry = withoutEntry.substring(FILE_PREFIX.length());
        }

        int slashIndex = withoutEntry.lastIndexOf('/');
        if (slashIndex > -1 && slashIndex < withoutEntry.length() - 1) {
            return withoutEntry.substring(slashIndex + 1);
        }
        return withoutEntry;
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
        for (PropertySourcesLocator propertySourcesLocator : propertySourcesLocators) {
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
