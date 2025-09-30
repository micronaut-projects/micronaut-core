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
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.BeanIntrospectionScanner;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.inject.BeanConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * <p>The default implementation of the {@link Environment} interface. Configures a named environment.</p>
 *
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
public class DefaultEnvironment extends PropertySourcePropertyResolver implements Environment {

    private static final List<PropertySource> CONSTANT_PROPERTY_SOURCES = StaticOptimizations.get(ConstantPropertySources.class)
            .map(ConstantPropertySources::getSources)
            .orElse(Collections.emptyList());

    private static final String EC2_LINUX_HYPERVISOR_FILE = "/sys/hypervisor/uuid";
    private static final String EC2_LINUX_BIOS_VENDOR_FILE = "/sys/devices/virtual/dmi/id/bios_vendor";
    private static final String EC2_WINDOWS_HYPERVISOR_CMD = "wmic path win32_computersystemproduct get uuid";
    private static final String FILE_SEPARATOR = ",";
    private static final String AWS_LAMBDA_FUNCTION_NAME_ENV = "AWS_LAMBDA_FUNCTION_NAME";
    private static final String K8S_ENV = "KUBERNETES_SERVICE_HOST";
    private static final String PCF_ENV = "VCAP_SERVICES";
    private static final String HEROKU_DYNO = "DYNO";
    private static final String GOOGLE_APPENGINE_ENVIRONMENT = "GAE_ENV";
    private static final int DEFAULT_READ_TIMEOUT = 500;
    private static final int DEFAULT_CONNECT_TIMEOUT = 500;
    // CHECKSTYLE:OFF
    private static final String GOOGLE_COMPUTE_METADATA = "metadata.google.internal";
    // CHECKSTYLE:ON
    private static final String ORACLE_CLOUD_ASSET_TAG_FILE = "/sys/devices/virtual/dmi/id/chassis_asset_tag";
    private static final String ORACLE_CLOUD_WINDOWS_ASSET_TAG_CMD = "wmic systemenclosure get smbiosassettag";
    private static final String DO_SYS_VENDOR_FILE = "/sys/devices/virtual/dmi/id/sys_vendor";
    private static final Boolean DEDUCE_ENVIRONMENT_DEFAULT = true;
    private static final List<String> DEFAULT_CONFIG_LOCATIONS = Arrays.asList("classpath:/", "file:config/");
    protected final ClassPathResourceLoader resourceLoader;
    protected final List<PropertySource> refreshablePropertySources = new ArrayList<>(10);
    protected final MutableConversionService mutableConversionService;
    private EnvironmentsAndPackage environmentsAndPackage;
    private final Set<String> names;
    private final ClassLoader classLoader;
    private final Collection<String> packages = new ConcurrentLinkedQueue<>();
    private final BeanIntrospectionScanner annotationScanner;
    private final Collection<String> configurationIncludes = new HashSet<>(3);
    private final Collection<String> configurationExcludes = new HashSet<>(3);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Collection<PropertySourceLoader> propertySourceLoaderList;
    private final Map<String, PropertySourceLoader> loaderByFormatMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> presenceCache = new ConcurrentHashMap<>();
    private final AtomicBoolean reading = new AtomicBoolean(false);
    private final Boolean deduceEnvironments;
    private final ApplicationContextConfiguration configuration;
    private final Collection<String> configLocations;

    /**
     * Construct a new environment for the given configuration.
     *
     * @param configuration The configuration
     */
    public DefaultEnvironment(@NonNull ApplicationContextConfiguration configuration) {
        this(configuration, true);
    }

    /**
     * Construct a new environment for the given configuration.
     *
     * @param configuration The configuration
     * @param logEnabled flag to enable or disable logger
     */
    public DefaultEnvironment(@NonNull ApplicationContextConfiguration configuration, boolean logEnabled) {
        super(configuration.getConversionService().orElseGet(MutableConversionService::create), logEnabled);
        this.mutableConversionService = (MutableConversionService) conversionService;
        this.configuration = configuration;
        this.resourceLoader = configuration.getResourceLoader();

        List<String> specifiedNames = new ArrayList<>(configuration.getEnvironments());

        specifiedNames.addAll(0, Stream.of(CachedEnvironment.getProperty(ENVIRONMENTS_PROPERTY),
                CachedEnvironment.getenv(ENVIRONMENTS_ENV))
                .filter(StringUtils::isNotEmpty)
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .toList());

        this.deduceEnvironments = configuration.getDeduceEnvironments().orElse(null);
        EnvironmentsAndPackage environmentsAndPackage = getEnvironmentsAndPackage(specifiedNames);
        if (environmentsAndPackage.enviroments.isEmpty() && specifiedNames.isEmpty()) {
            specifiedNames = configuration.getDefaultEnvironments();
        }
        Set<String> environments = new LinkedHashSet<>(environmentsAndPackage.enviroments);
        String aPackage = environmentsAndPackage.aPackage;
        if (aPackage != null) {
            packages.add(aPackage);
        }

        specifiedNames.forEach(environments::remove);
        environments.addAll(specifiedNames);
        this.classLoader = configuration.getClassLoader();
        this.annotationScanner = createAnnotationScanner(classLoader);
        this.names = environments;
        if (!environments.isEmpty()) {
            log.info("Established active environments: {}", environments);
        }
        List<String> configLocations = configuration.getOverrideConfigLocations() == null
            ? new ArrayList<>(DEFAULT_CONFIG_LOCATIONS)
            : new ArrayList<>(configuration.getOverrideConfigLocations());
        // Search config locations in reverse order
        Collections.reverse(configLocations);
        this.configLocations = configLocations;
    }

    @Override
    public boolean isPresent(String className) {
        return presenceCache.computeIfAbsent(className, s -> ClassUtils.isPresent(className, getClassLoader()));
    }

    @Override
    public PropertyPlaceholderResolver getPlaceholderResolver() {
        return this.propertyPlaceholderResolver;
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
            resetCaches();
            processPropertySource(propertySource, PropertySource.PropertyConvention.JAVA_PROPERTIES);
        }
        return this;
    }

    @Override
    public Environment removePropertySource(PropertySource propertySource) {
        propertySources.remove(propertySource.getName());
        if (isRunning() && !reading.get()) {
            resetCaches();
        }
        return this;
    }

    @Override
    public DefaultEnvironment addPropertySource(String name, Map<String, ? super Object> values) {
        return (DefaultEnvironment) super.addPropertySource(name, values);
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
        return Collections.unmodifiableCollection(this.propertySources.values());
    }

    @Override
    public Environment start() {
        if (running.compareAndSet(false, true)) {
            log.debug("Starting environment {} for active names {}", this, getActiveNames());
            if (reading.compareAndSet(false, true)) {

                readPropertySources(getPropertySourceRootName());
                reading.set(false);
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
        reading.set(false);
        this.propertySources.values().removeAll(refreshablePropertySources);
        synchronized (catalog) {
            Arrays.fill(catalog, null);
            resetCaches();
        }
        return this;
    }

    @Override
    public Map<String, Object> refreshAndDiff() {
        Map<String, DefaultPropertyEntry>[] copiedCatalog = copyCatalog();
        refresh();
        return diffCatalog(copiedCatalog, catalog);
    }

    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context) {
        return mutableConversionService.convert(object, targetType, context);
    }

    @Override
    public <S, T> Optional<T> convert(S object, Class<? super S> sourceType, Class<T> targetType, ConversionContext context) {
        return mutableConversionService.convert(object, sourceType, targetType, context);
    }

    @Override
    public <S, T> boolean canConvert(Class<S> sourceType, Class<T> targetType) {
        return mutableConversionService.canConvert(sourceType, targetType);
    }

    @Override
    public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        mutableConversionService.addConverter(sourceType, targetType, typeConverter);
    }

    @Override
    public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Function<S, T> typeConverter) {
        mutableConversionService.addConverter(sourceType, targetType, typeConverter);
    }

    /**
     * @return The mutable conversion service.
     */
    @Internal
    public MutableConversionService getMutableConversionService() {
        return mutableConversionService;
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

    /**
     * @return Whether environment names and packages should be deduced
     */
    protected boolean shouldDeduceEnvironments() {
        if (deduceEnvironments != null) {
            log.debug("Environment deduction was set explicitly via builder to: {}", deduceEnvironments);

            return deduceEnvironments;
        } else if (configuration.isEnableDefaultPropertySources()) {
            String deduceProperty = CachedEnvironment.getProperty(Environment.DEDUCE_ENVIRONMENT_PROPERTY);
            String deduceEnv = CachedEnvironment.getenv(Environment.DEDUCE_ENVIRONMENT_ENV);

            if (StringUtils.isNotEmpty(deduceEnv)) {
                boolean deduce = Boolean.parseBoolean(deduceEnv);
                log.debug("Environment deduction was set via environment variable to: {}", deduce);
                return deduce;
            } else if (StringUtils.isNotEmpty(deduceProperty)) {
                boolean deduce = Boolean.parseBoolean(deduceProperty);
                log.debug("Environment deduction was set via system property to: {}", deduce);
                return deduce;
            } else {
                boolean deduceDefault = DEDUCE_ENVIRONMENT_DEFAULT;
                log.debug("Environment deduction is using the default of: {}", deduceDefault);
                return deduceDefault;
            }
        } else {
            return false;
        }
    }

    /**
     * @return Whether cloud environment should be deduced based on environment variable, system property or configuration
     */
    protected boolean shouldDeduceCloudEnvironment() {
        String deduceEnv = CachedEnvironment.getenv(Environment.DEDUCE_CLOUD_ENVIRONMENT_ENV);
        if (StringUtils.isNotEmpty(deduceEnv)) {
            boolean deduce = Boolean.parseBoolean(deduceEnv);
            log.debug("Cloud environment deduction was set via environment variable to: {}", deduce);
            return deduce;
        }
        String deduceProperty = CachedEnvironment.getProperty(Environment.DEDUCE_CLOUD_ENVIRONMENT_PROPERTY);
        if (StringUtils.isNotEmpty(deduceProperty)) {
            boolean deduce = Boolean.parseBoolean(deduceProperty);
            log.debug("Cloud environment deduction was set via system property to: {}", deduce);
            return deduce;
        }
        return configuration.isDeduceCloudEnvironment();
    }

    /**
     * Creates the default annotation scanner.
     *
     * @param classLoader The class loader
     * @return The scanner
     */
    protected BeanIntrospectionScanner createAnnotationScanner(ClassLoader classLoader) {
        return new BeanIntrospectionScanner();
    }

    /**
     * @return The property source root name
     */
    protected String getPropertySourceRootName() {
        return DEFAULT_NAME;
    }

    /**
     * @param name The name to read property sources
     */
    protected void readPropertySources(String name) {
        refreshablePropertySources.clear();
        List<PropertySource> propertySources;
        if (configuration.isEnableDefaultPropertySources()) {
            propertySources = readPropertySourceList(name);
            addDefaultPropertySources(propertySources);
            String propertySourcesSystemProperty = CachedEnvironment.getProperty(Environment.PROPERTY_SOURCES_KEY);
            if (propertySourcesSystemProperty != null) {
                propertySources.addAll(readPropertySourceListFromFiles(propertySourcesSystemProperty));
            }
            String propertySourcesEnv = readPropertySourceListKeyFromEnvironment();
            if (propertySourcesEnv != null) {
                propertySources.addAll(readPropertySourceListFromFiles(propertySourcesEnv));
            }
            refreshablePropertySources.addAll(propertySources);
            readConstantPropertySources(name, propertySources);
        } else {
            propertySources = new ArrayList<>(this.propertySources.size());
        }

        propertySources.addAll(this.propertySources.values());
        OrderUtil.sortOrdered(propertySources);
        for (PropertySource propertySource : propertySources) {
            log.debug("Processing property source: {}", propertySource.getName());
            processPropertySource(propertySource, propertySource.getConvention());
        }
    }

    private void readConstantPropertySources(String name, List<PropertySource> propertySources) {
        Set<String> activeNames = getActiveNames();
        Set<String> propertySourceNames = CollectionUtils.newHashSet(activeNames.size() + 1);
        propertySourceNames.add(name);
        for (String env : activeNames) {
            propertySourceNames.add(name + "-" + env);
        }
        for (PropertySource p : getConstantPropertySources()) {
            if (propertySourceNames.contains(p.getName())) {
                propertySources.add(p);
            }
        }
    }

    /**
     * @return Property sources created at build time
     */
    protected List<PropertySource> getConstantPropertySources() {
        return CONSTANT_PROPERTY_SOURCES;
    }

    /**
     * Reads the value of MICRONAUT_CONFIG_FILES environment variable.
     *
     * @return The comma-separated list of files
     */
    protected String readPropertySourceListKeyFromEnvironment() {
        return CachedEnvironment.getenv(StringUtils.convertDotToUnderscore(Environment.PROPERTY_SOURCES_KEY));
    }

    /**
     * Resolve the property sources for files passed via system property and system env.
     *
     * @param files The comma separated list of files
     * @return The list of property sources for each file
     */
    protected List<PropertySource> readPropertySourceListFromFiles(String files) {
        List<PropertySource> propertySources = new ArrayList<>();
        Collection<PropertySourceLoader> propertySourceLoaders = getPropertySourceLoaders();
        Optional<Collection<String>> filePathList = Optional.ofNullable(files)
            .filter(value -> !value.isEmpty())
            .map(value -> value.split(FILE_SEPARATOR))
            .map(Arrays::asList)
            .map(Collections::unmodifiableList);

        filePathList.ifPresent(list -> {
            if (!list.isEmpty()) {
                int order = AbstractPropertySourceLoader.DEFAULT_POSITION + 50;
                for (String filePath: list) {
                    if (!propertySourceLoaders.isEmpty()) {
                        String extension = NameUtils.extension(filePath);
                        String fileName = NameUtils.filename(filePath);
                        Optional<PropertySourceLoader> propertySourceLoader = Optional.ofNullable(loaderByFormatMap.get(extension));
                        if (propertySourceLoader.isPresent()) {
                            log.debug("Reading property sources from loader: {}", propertySourceLoader);
                            Optional<Map<String, Object>> properties = readPropertiesFromLoader(fileName, filePath, propertySourceLoader.get());
                            if (properties.isPresent()) {
                                propertySources.add(PropertySource.of(filePath, properties.get(), order));
                            }
                            order++;
                        } else {
                            throw new ConfigurationException("Unsupported properties file format while reading " + fileName + "." + extension + " from " + filePath);
                        }
                    }
                }
            }
        });
        return propertySources;
    }

    /**
     * @param name The name to resolver property sources
     * @return The list of property sources
     */
    protected List<PropertySource> readPropertySourceList(String name) {
        List<PropertySource> propertySources = new ArrayList<>();
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
                log.debug("Reading property sources from loader: {}", propertySourceLoader);
                loadPropertySourceFromLoader(name, propertySourceLoader, propertySources, resourceLoader);
            }
        }
    }

    /**
     * Adds default property sources.
     *
     * @param propertySources The list of property sources
     */
    protected void addDefaultPropertySources(List<PropertySource> propertySources) {
        if (!this.propertySources.containsKey(SystemPropertiesPropertySource.NAME)) {
            propertySources.add(new SystemPropertiesPropertySource());
        }
        if (!this.propertySources.containsKey(EnvironmentPropertySource.NAME) && configuration.isEnvironmentPropertySource()) {
            List<String> includes = configuration.getEnvironmentVariableIncludes();
            List<String> excludes = configuration.getEnvironmentVariableExcludes();
            if (this.names.contains(Environment.KUBERNETES)) {
                propertySources.add(new KubernetesEnvironmentPropertySource(includes, excludes));
            } else {
                propertySources.add(new EnvironmentPropertySource(includes, excludes));
            }
        }
    }

    /**
     * @return Loaded properties as a {@link SoftServiceLoader}
     */
    protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
        return SoftServiceLoader.load(PropertySourceLoader.class, getClassLoader());
    }

    @Override
    public Optional<PropertyEntry> getPropertyEntry(String name) {
        for (PropertyCatalog propertyCatalog : PropertyCatalog.values()) {
            Map<String, DefaultPropertyEntry> entries = resolveEntriesForKey(name, false, propertyCatalog);
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

    @SuppressWarnings("MagicNumber")
    private Collection<PropertySourceLoader> evaluatePropertySourceLoaders() {
        SoftServiceLoader<PropertySourceLoader> definitions = readPropertySourceLoaders();
        Collection<PropertySourceLoader> allLoaders = definitions.collectAll();
        for (PropertySourceLoader propertySourceLoader : allLoaders) {
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

    private EnvironmentsAndPackage getEnvironmentsAndPackage(List<String> specifiedNames) {
        EnvironmentsAndPackage environmentsAndPackage = this.environmentsAndPackage;
        boolean isNotFunction = !specifiedNames.contains(Environment.FUNCTION);
        final boolean deduceEnvironment = shouldDeduceEnvironments();
        final boolean deduceCloudEnvironmentUsingProbes = isNotFunction && shouldDeduceCloudEnvironment();
        if (environmentsAndPackage == null) {
            synchronized (EnvironmentsAndPackage.class) { // double check
                environmentsAndPackage = this.environmentsAndPackage;
                if (environmentsAndPackage == null) {
                    environmentsAndPackage = deduceEnvironmentsAndPackage(
                            deduceEnvironment,
                            deduceCloudEnvironmentUsingProbes,
                            isNotFunction,
                            !deduceCloudEnvironmentUsingProbes
                    );
                    this.environmentsAndPackage = environmentsAndPackage;
                }
            }
        }
        return environmentsAndPackage;
    }

    private static EnvironmentsAndPackage deduceEnvironmentsAndPackage(
            boolean deduceEnvironments,
            boolean deduceComputePlatform,
            boolean inspectTrace,
            boolean deduceFunctionPlatform
        ) {

        EnvironmentsAndPackage environmentsAndPackage = new EnvironmentsAndPackage();
        Set<String> environments = environmentsAndPackage.enviroments;

        if (inspectTrace) {
            performStackTraceInspection(deduceEnvironments, environmentsAndPackage, environments);
        }

        if (deduceEnvironments && !environments.contains(ANDROID)) {
            performEnvironmentDeduction(deduceComputePlatform, environments);
        }

        if (deduceFunctionPlatform) {
            performFunctionDeduction(environments);
        }

        return environmentsAndPackage;
    }

    private static void performFunctionDeduction(Set<String> environments) {
        // deduce AWS Lambda
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(AWS_LAMBDA_FUNCTION_NAME_ENV))) {
            environments.add(Environment.AMAZON_EC2);
            environments.add(Environment.CLOUD);
        }
    }

    private static void performEnvironmentDeduction(boolean deduceComputePlatform, Set<String> environments) {
        // deduce k8s
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(K8S_ENV))) {
            environments.add(Environment.KUBERNETES);
            environments.add(Environment.CLOUD);
        }
        // deduce CF
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(PCF_ENV))) {
            environments.add(Environment.CLOUD_FOUNDRY);
            environments.add(Environment.CLOUD);
        }

        // deduce heroku
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(HEROKU_DYNO))) {
            environments.add(Environment.HEROKU);
            environments.add(Environment.CLOUD);
            deduceComputePlatform = false;
        }

        // deduce GAE
        if (StringUtils.isNotEmpty(CachedEnvironment.getenv(GOOGLE_APPENGINE_ENVIRONMENT))) {
            environments.add(Environment.GAE);
            environments.add(Environment.GOOGLE_COMPUTE);
            environments.add(Environment.CLOUD);
            deduceComputePlatform = false;
        }

        if (deduceComputePlatform) {
            performComputePlatformDeduction(environments);
        }
    }

    private static void performComputePlatformDeduction(Set<String> environments) {
        ComputePlatform computePlatform = determineCloudProvider();
        if (computePlatform != null) {
            switch (computePlatform) {
                case GOOGLE_COMPUTE:
                    //instantiate bean for GC metadata discovery
                    environments.add(GOOGLE_COMPUTE);
                    environments.add(Environment.CLOUD);
                    break;
                case AMAZON_EC2:
                    //instantiate bean for ec2 metadata discovery
                    environments.add(AMAZON_EC2);
                    environments.add(Environment.CLOUD);
                    break;
                case ORACLE_CLOUD:
                    environments.add(ORACLE_CLOUD);
                    environments.add(Environment.CLOUD);
                    break;
                case AZURE:
                    // not yet implemented
                    environments.add(AZURE);
                    environments.add(Environment.CLOUD);
                    break;
                case IBM:
                    // not yet implemented
                    environments.add(IBM);
                    environments.add(Environment.CLOUD);
                    break;
                case DIGITAL_OCEAN:
                    environments.add(DIGITAL_OCEAN);
                    environments.add(Environment.CLOUD);
                    break;
                case OTHER:
                    // do nothing here
                    break;
                default:
                    // no-op
            }
        }
    }

    private static void performStackTraceInspection(boolean deduceEnvironments, EnvironmentsAndPackage environmentsAndPackage, Set<String> environments) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        int len = stackTrace.length;
        for (int i = 0; i < len; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            String className = stackTraceElement.getClassName();

            analyzeStackTraceElement(deduceEnvironments, environmentsAndPackage, environments, stackTrace, len, i, stackTraceElement, className);
        }
    }

    private static void analyzeStackTraceElement(boolean deduceEnvironments, EnvironmentsAndPackage environmentsAndPackage, Set<String> environments, StackTraceElement[] stackTrace, int len, int i, StackTraceElement stackTraceElement, String className) {
        if (className.startsWith("io.micronaut")) {
            int nextIndex = i + 1;
            if (nextIndex < len) {
                StackTraceElement next = stackTrace[nextIndex];
                if (!next.getClassName().startsWith("io.micronaut")) {
                    environmentsAndPackage.aPackage = NameUtils.getPackageName(next.getClassName());
                }
            }
        }

        if (stackTraceElement.getMethodName().contains("$spock_")) {
            environmentsAndPackage.aPackage = NameUtils.getPackageName(className);
        }

        if (deduceEnvironments) {
            if (Stream.of("org.spockframework", "org.junit", "io.kotlintest", "io.kotest").anyMatch(className::startsWith)) {
                environments.add(TEST);
            }

            if (className.startsWith("com.android")) {
                environments.add(ANDROID);
            }
        }
    }

    private Map<String, Object> diffCatalog(Map<String, DefaultPropertyEntry>[] original, Map<String, DefaultPropertyEntry>[] newCatalog) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (int i = 0; i < original.length; i++) {
            Map<String, DefaultPropertyEntry> map = original[i];
            Map<String, DefaultPropertyEntry> newMap = newCatalog[i];
            boolean hasNew = newMap != null;
            boolean hasOld = map != null;
            if (!hasOld && hasNew) {
                changes.putAll(newMap);
            } else {
                if (!hasNew && hasOld) {
                    changes.putAll(map);
                } else if (hasOld && hasNew) {
                    diffMap(map, newMap, changes);
                }
            }
        }
        if (!changes.isEmpty()) {
            Map<String, Object> placeholdersAltered = new LinkedHashMap<>();
            for (Map<String, DefaultPropertyEntry> map :
                newCatalog) {
                if (map != null) {
                    map.forEach((key, v) -> {
                        if (v.value() instanceof String val) {
                            for (String changed : changes.keySet()) {
                                if (val.contains(changed)) {
                                    placeholdersAltered.put(key, v.value());
                                }
                            }
                        }
                    });
                }
            }
            changes.putAll(placeholdersAltered);
        }
        return changes;
    }

    private void diffMap(
        Map<String, DefaultPropertyEntry> map,
        Map<String, DefaultPropertyEntry> newMap,
        Map<String, Object> changes) {
        Map<String, DefaultPropertyEntry> remainingMap = new LinkedHashMap<>(map);
        for (Map.Entry<String, DefaultPropertyEntry> entry : newMap.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue().value();
            if (!map.containsKey(key)) {
                changes.put(key, newValue);
            } else {
                Object oldValue = map.getOrDefault(key, PropertySourcePropertyResolver.NULL_ENTRY).value();
                boolean hasNew = newValue != null;
                boolean hasOld = oldValue != null;
                if (hasNew && !hasOld) {
                    changes.put(key, null);
                } else if (hasOld && !hasNew) {
                    changes.put(key, oldValue);
                } else if (hasNew && hasOld && hasChanged(newValue, oldValue)) {
                    changes.put(key, oldValue);
                }
                remainingMap.remove(key);
            }
        }
        remainingMap.forEach((key, value) -> {
            changes.put(key, value.value());
        });
    }

    private static boolean hasChanged(Object newValue, Object oldValue) {
        return !Objects.deepEquals(newValue, oldValue);
    }

    private Map<String, DefaultPropertyEntry>[] copyCatalog() {
        Map<String, DefaultPropertyEntry>[] newCatalog = new Map[catalog.length];
        for (int i = 0; i < catalog.length; i++) {
            Map<String, DefaultPropertyEntry> entry = catalog[i];
            if (entry != null) {
                newCatalog[i] = new LinkedHashMap<>(entry);
            }
        }
        return newCatalog;
    }

    private static ComputePlatform determineCloudProvider() {
        String computePlatform = CachedEnvironment.getProperty(CLOUD_PLATFORM_PROPERTY);
        if (computePlatform != null) {

            try {
                return ComputePlatform.valueOf(computePlatform);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Illegal value specified for [" + CLOUD_PLATFORM_PROPERTY + "]: " + computePlatform);
            }

        }
        boolean isWindows = CachedEnvironment.getProperty("os.name")
            .toLowerCase().startsWith("windows");

        if (isWindows ? isEC2Windows() : isEC2Linux()) {
            return ComputePlatform.AMAZON_EC2;
        }

        if (isGoogleCompute()) {
            return ComputePlatform.GOOGLE_COMPUTE;
        }

        if (isWindows ? isOracleCloudWindows() : isOracleCloudLinux()) {
            return ComputePlatform.ORACLE_CLOUD;
        }

        if (isDigitalOcean()) {
            return ComputePlatform.DIGITAL_OCEAN;
        }

        //TODO check for azure and IBM
        //Azure - see https://blog.mszcool.com/index.php/2015/04/detecting-if-a-virtual-machine-runs-in-microsoft-azure-linux-windows-to-protect-your-software-when-distributed-via-the-azure-marketplace/
        //IBM - uses cloudfoundry, will have to use that to probe
        // if all else fails not a cloud server that we can tell
        return ComputePlatform.BARE_METAL;
    }

    @SuppressWarnings("MagicNumber")
    private static boolean isGoogleCompute() {
        try {
            InetAddress.getByName(GOOGLE_COMPUTE_METADATA);
            return true;
        } catch (Exception e) {
            // well not google then
        }
        return false;
    }

    @SuppressWarnings("MagicNumber")
    private static boolean isOracleCloudLinux() {
        return readFile(ORACLE_CLOUD_ASSET_TAG_FILE).toLowerCase().contains("oraclecloud");
    }

    private static Optional<Process> runWindowsCmd(String cmd) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("cmd.exe", "/c", cmd);
            builder.redirectErrorStream(true);
            builder.directory(new File(CachedEnvironment.getProperty("user.home")));
            Process process = builder.start();
            return Optional.of(process);
        } catch (IOException e) {

        }
        return Optional.empty();
    }

    private static StringBuilder readProcessStream(Process process) {
        StringBuilder stdout = new StringBuilder();

        try {
            //Read out dir output
            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                stdout.append(line);
            }
        } catch (IOException e) {
            // ignore
        }

        return stdout;
    }

    private static boolean isOracleCloudWindows() {
        Optional<Process> optionalProcess = runWindowsCmd(ORACLE_CLOUD_WINDOWS_ASSET_TAG_CMD);
        if (!optionalProcess.isPresent()) {
            return false;
        }
        Process process = optionalProcess.get();
        StringBuilder stdout = readProcessStream(process);

        //Wait to get exit value
        try {
            int exitValue = process.waitFor();
            if (exitValue == 0 && stdout.toString().toLowerCase().contains("oraclecloud")) {
                return true;
            }
        } catch (InterruptedException e) {
            // test negative
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private static boolean isEC2Linux() {
        if (readFile(EC2_LINUX_HYPERVISOR_FILE).startsWith("ec2")) {
            return true;
        } else if (readFile(EC2_LINUX_BIOS_VENDOR_FILE).toLowerCase().startsWith("amazon ec2")) {
            return true;
        }

        return false;
    }

    private static String readFile(String path) {
        try {
            Path pathPath = Paths.get(path);
            if (!Files.exists(pathPath)) {
                return "";
            }
            return new String(Files.readAllBytes(pathPath)).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean isEC2Windows() {
        Optional<Process> optionalProcess = runWindowsCmd(EC2_WINDOWS_HYPERVISOR_CMD);
        if (!optionalProcess.isPresent()) {
            return false;
        }
        Process process = optionalProcess.get();
        StringBuilder stdout = readProcessStream(process);
        //Wait to get exit value
        try {
            int exitValue = process.waitFor();
            if (exitValue == 0 && stdout.toString().startsWith("EC2")) {
                return true;
            }
        } catch (InterruptedException e) {
            // test negative
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private static boolean isDigitalOcean() {
        return "digitalocean".equalsIgnoreCase(readFile(DO_SYS_VENDOR_FILE));
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close!", e);
        }
        stop();
    }

    /**
     * Helper class for handling environments and package.
     */
    private static class EnvironmentsAndPackage {
        String aPackage;
        Set<String> enviroments = new LinkedHashSet<>(1);
    }
}
