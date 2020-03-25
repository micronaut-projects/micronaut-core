/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.env;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.CachingClassPathAnnotationScanner;
import io.micronaut.core.io.scan.ClassPathAnnotationScanner;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.annotation.Annotation;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
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
 * @author rvanderwerf
 * @since 1.0
 */
public class DefaultEnvironment extends PropertySourcePropertyResolver implements Environment {

    private static final String EC2_LINUX_HYPERVISOR_FILE = "/sys/hypervisor/uuid";
    private static final String EC2_LINUX_BIOS_VENDOR_FILE = "/sys/devices/virtual/dmi/id/bios_vendor";
    private static final String EC2_WINDOWS_HYPERVISOR_CMD = "wmic path win32_computersystemproduct get uuid";
    private static final String FILE_SEPARATOR = ",";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultEnvironment.class);
    private static final String AWS_LAMBDA_FUNCTION_NAME_ENV = "AWS_LAMBDA_FUNCTION_NAME";
    private static final String K8S_ENV = "KUBERNETES_SERVICE_HOST";
    private static final String PCF_ENV = "VCAP_SERVICES";
    private static final String HEROKU_DYNO = "DYNO";
    private static final String GOOGLE_APPENGINE_ENVIRONMENT = "GAE_ENV";
    private static final int DEFAULT_READ_TIMEOUT = 500;
    private static final int DEFAULT_CONNECT_TIMEOUT = 500;
    private static final String GOOGLE_COMPUTE_METADATA = "http://metadata.google.internal";
    private static final String ORACLE_CLOUD_ASSET_TAG_FILE = "/sys/devices/virtual/dmi/id/chassis_asset_tag";
    private static final String ORACLE_CLOUD_WINDOWS_ASSET_TAG_CMD = "wmic systemenclosure get smbiosassettag";
    private static final String DO_SYS_VENDOR_FILE = "/sys/devices/virtual/dmi/id/sys_vendor";
    private static final Boolean DEDUCE_ENVIRONMENT_DEFAULT = true;

    protected final ClassPathResourceLoader resourceLoader;
    protected final List<PropertySource> refreshablePropertySources = new ArrayList<>(10);

    private EnvironmentsAndPackage environmentsAndPackage;

    private final Set<String> names;
    private final ClassLoader classLoader;
    private final Collection<String> packages = new ConcurrentLinkedQueue<>();
    private final ClassPathAnnotationScanner annotationScanner;
    private Collection<String> configurationIncludes = new HashSet<>(3);
    private Collection<String> configurationExcludes = new HashSet<>(3);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Collection<PropertySourceLoader> propertySourceLoaderList;
    private final Map<String, PropertySourceLoader> loaderByFormatMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> presenceCache = new ConcurrentHashMap<>();
    private final AtomicBoolean reading = new AtomicBoolean(false);
    private final Boolean deduceEnvironments;
    private final ApplicationContextConfiguration configuration;

    /**
     * Construct a new environment for the given configuration.
     *
     * @param configuration The configuration
     */
    public DefaultEnvironment(@NonNull ApplicationContextConfiguration configuration) {
        super(configuration.getConversionService());
        this.configuration = configuration;
        Set<String> environments = new LinkedHashSet<>(3);
        List<String> specifiedNames = configuration.getEnvironments();

        this.deduceEnvironments = configuration.getDeduceEnvironments().orElse(null);
        EnvironmentsAndPackage environmentsAndPackage = getEnvironmentsAndPackage(specifiedNames);
        environments.addAll(environmentsAndPackage.enviroments);
        String aPackage = environmentsAndPackage.aPackage;
        if (aPackage != null) {
            packages.add(aPackage);
        }

        environments.addAll(specifiedNames);

        this.classLoader = configuration.getClassLoader();
        this.names = environments;
        if (LOG.isInfoEnabled() && !environments.isEmpty()) {
            LOG.info("Established active environments: {}", environments);
        }
        this.resourceLoader = configuration.getResourceLoader();
        this.annotationScanner = createAnnotationScanner(classLoader);
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
    public Stream<Class> scan(Class<? extends Annotation> annotation) {
        return annotationScanner.scan(annotation, getPackages());
    }

    @Override
    public Stream<Class> scan(Class<? extends Annotation> annotation, String... packages) {
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
            if (LOG.isDebugEnabled()) {
                LOG.debug("Starting environment {} for active names {}", this, getActiveNames());
            }
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
            for (int i = 0; i < catalog.length; i++) {
                catalog[i] = null;
            }
            resetCaches();
        }
        return this;
    }

    @Override
    public Map<String, Object> refreshAndDiff() {
        Map<String, Object>[] copiedCatalog = copyCatalog();
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
            if (LOG.isDebugEnabled()) {
                LOG.debug("Environment deduction was set explicitly via builder to: " + deduceEnvironments);
            }

            return deduceEnvironments;
        } else {
            String deduceProperty = System.getProperty(Environment.DEDUCE_ENVIRONMENT_PROPERTY);
            String deduceEnv = System.getenv(Environment.DEDUCE_ENVIRONMENT_ENV);

            if (StringUtils.isNotEmpty(deduceEnv)) {
                boolean deduce = Boolean.valueOf(deduceEnv);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Environment deduction was set via environment variable to: " + deduce);
                }
                return deduce;
            } else if (StringUtils.isNotEmpty(deduceProperty)) {
                boolean deduce = Boolean.valueOf(deduceProperty);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Environment deduction was set via system property to: " + deduce);
                }
                return deduce;
            } else {
                boolean deduceDefault = DEDUCE_ENVIRONMENT_DEFAULT;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Environment deduction is using the default of: " + deduceDefault);
                }
                return deduceDefault;
            }
        }
    }


    /**
     * Creates the default annotation scanner.
     *
     * @param classLoader The class loader
     * @return The scanner
     */
    protected ClassPathAnnotationScanner createAnnotationScanner(ClassLoader classLoader) {
        return new CachingClassPathAnnotationScanner(classLoader);
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
        List<PropertySource> propertySources = readPropertySourceList(name);
        addDefaultPropertySources(propertySources);
        propertySources.addAll(readPropertySourceListFromFiles(System.getProperty(Environment.PROPERTY_SOURCES_KEY)));
        propertySources.addAll(readPropertySourceListFromFiles(
            readPropertySourceListKeyFromEnvironment())
        );
        refreshablePropertySources.addAll(propertySources);

        propertySources.addAll(this.propertySources.values());
        OrderUtil.sort(propertySources);
        for (PropertySource propertySource : propertySources) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Processing property source: {}", propertySource.getName());
            }
            processPropertySource(propertySource, propertySource.getConvention());
        }
    }

    /**
     * Reads the value of MICRONAUT_CONFIG_FILES environment variable.
     *
     * @return The comma-separated list of files
     */
    protected String readPropertySourceListKeyFromEnvironment() {
        return System.getenv(StringUtils.convertDotToUnderscore(Environment.PROPERTY_SOURCES_KEY));
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
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Reading property sources from loader: {}", propertySourceLoader);
                            }
                            Optional<Map<String, Object>> properties = readPropertiesFromLoader(fileName, filePath, propertySourceLoader.get());
                            if (properties.isPresent()) {
                                propertySources.add(PropertySource.of(filePath, properties.get(), order));
                            }
                            order++;
                        } else {
                            throw new ConfigurationException("Unsupported properties file format: " + fileName);
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
        Collection<PropertySourceLoader> propertySourceLoaders = getPropertySourceLoaders();
        if (propertySourceLoaders.isEmpty()) {
            loadPropertySourceFromLoader(name, new PropertiesPropertySourceLoader(), propertySources);
        } else {
            for (PropertySourceLoader propertySourceLoader : propertySourceLoaders) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Reading property sources from loader: {}", propertySourceLoader);
                }
                loadPropertySourceFromLoader(name, propertySourceLoader, propertySources);
            }
        }
        return propertySources;
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
            propertySources.add(new EnvironmentPropertySource(
                    configuration.getEnvironmentVariableIncludes(),
                    configuration.getEnvironmentVariableExcludes()));
        }
    }

    /**
     * @return Loaded properties as a {@link SoftServiceLoader}
     */
    protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
        return SoftServiceLoader.load(PropertySourceLoader.class, getClassLoader());
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
        Collection<PropertySourceLoader> allLoaders = new ArrayList<>(10);
        for (ServiceDefinition<PropertySourceLoader> definition : definitions) {
            if (definition.isPresent()) {
                PropertySourceLoader loader = definition.load();
                allLoaders.add(loader);
                Set<String> extensions = loader.getExtensions();
                for (String extension : extensions) {
                    loaderByFormatMap.put(extension, loader);
                }
            }
        }
        return allLoaders;
    }

    private void loadPropertySourceFromLoader(String name, PropertySourceLoader propertySourceLoader, List<PropertySource> propertySources) {
        Optional<PropertySource> defaultPropertySource = propertySourceLoader.load(name, this);
        defaultPropertySource.ifPresent(propertySources::add);
        Set<String> activeNames = getActiveNames();
        int i = 0;
        for (String activeName: activeNames) {
            Optional<PropertySource> propertySource = propertySourceLoader.loadEnv(name, this, ActiveEnvironment.of(activeName, i));
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
     * @throws ConfigurationException If unable to find the appropriate property soruce loader for the given file
     */
    private Optional<Map<String, Object>> readPropertiesFromLoader(String fileName, String filePath, PropertySourceLoader propertySourceLoader) throws ConfigurationException {
        ResourceResolver resourceResolver = new ResourceResolver();
        Optional<ResourceLoader> resourceLoader = resourceResolver.getSupportingLoader(filePath);
        ResourceLoader loader = resourceLoader.orElse(FileSystemResourceLoader.defaultLoader());
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
        final boolean extendedDeduction = !specifiedNames.contains(Environment.FUNCTION);
        if (environmentsAndPackage == null) {
            synchronized (EnvironmentsAndPackage.class) { // double check
                environmentsAndPackage = this.environmentsAndPackage;
                if (environmentsAndPackage == null) {
                    environmentsAndPackage = deduceEnvironmentsAndPackage(
                            shouldDeduceEnvironments(),
                            extendedDeduction,
                            extendedDeduction,
                            !extendedDeduction
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
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            ListIterator<StackTraceElement> stackTraceIterator = Arrays.asList(stackTrace).listIterator();

            while (stackTraceIterator.hasNext()) {
                StackTraceElement stackTraceElement = stackTraceIterator.next();
                String className = stackTraceElement.getClassName();

                if (className.startsWith("io.micronaut")) {
                    if (stackTraceIterator.hasNext()) {
                        StackTraceElement next = stackTrace[stackTraceIterator.nextIndex()];
                        if (!next.getClassName().startsWith("io.micronaut")) {
                            environmentsAndPackage.aPackage = NameUtils.getPackageName(next.getClassName());
                        }
                    }
                }

                if (stackTraceElement.getMethodName().contains("$spock_")) {
                    environmentsAndPackage.aPackage = NameUtils.getPackageName(className);
                }

                if (deduceEnvironments) {
                    if (Stream.of("org.spockframework", "org.junit", "io.kotlintest").anyMatch(className::startsWith)) {
                        environments.add(TEST);
                    }

                    if (className.startsWith("com.android")) {
                        environments.add(ANDROID);
                    }
                }
            }
        }

        if (deduceEnvironments) {
            if (!environments.contains(ANDROID)) {
                // deduce k8s
                if (StringUtils.isNotEmpty(System.getenv(K8S_ENV))) {
                    environments.add(Environment.KUBERNETES);
                    environments.add(Environment.CLOUD);
                }
                // deduce CF
                if (StringUtils.isNotEmpty(System.getenv(PCF_ENV))) {
                    environments.add(Environment.CLOUD_FOUNDRY);
                    environments.add(Environment.CLOUD);
                }

                // deduce heroku
                if (StringUtils.isNotEmpty(System.getenv(HEROKU_DYNO))) {
                    environments.add(Environment.HEROKU);
                    environments.add(Environment.CLOUD);
                    deduceComputePlatform = false;
                }

                // deduce GAE
                if (StringUtils.isNotEmpty(System.getenv(GOOGLE_APPENGINE_ENVIRONMENT))) {
                    environments.add(Environment.GAE);
                    environments.add(Environment.GOOGLE_COMPUTE);
                    deduceComputePlatform = false;
                }

                if (deduceComputePlatform) {
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
            }
        }

        if (deduceFunctionPlatform) {
            // deduce AWS Lambda
            if (StringUtils.isNotEmpty(System.getenv(AWS_LAMBDA_FUNCTION_NAME_ENV))) {
                environments.add(Environment.AMAZON_EC2);
                environments.add(Environment.CLOUD);
            }
        }

        Stream.of(System.getProperty(ENVIRONMENTS_PROPERTY),
            System.getenv(ENVIRONMENTS_ENV))
            .filter(StringUtils::isNotEmpty)
            .flatMap(s -> Arrays.stream(s.split(",")))
            .map(String::trim)
            .forEach(environments::add);

        return environmentsAndPackage;
    }

    private Map<String, Object> diffCatalog(Map<String, Object>[] original, Map<String, Object>[] newCatalog) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (int i = 0; i < original.length; i++) {
            Map<String, Object> map = original[i];
            Map<String, Object> newMap = newCatalog[i];
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
        return changes;
    }

    private void diffMap(Map<String, Object> map, Map<String, Object> newMap, Map<String, Object> changes) {
        for (Map.Entry<String, Object> entry : newMap.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            if (!map.containsKey(key)) {
                changes.put(key, newValue);
            } else {
                Object oldValue = map.get(key);
                boolean hasNew = newValue != null;
                boolean hasOld = oldValue != null;
                if (hasNew && !hasOld) {
                    changes.put(key, null);
                } else if (hasOld && !hasNew) {
                    changes.put(key, oldValue);
                } else if (hasNew && hasOld && !newValue.equals(oldValue)) {
                    changes.put(key, oldValue);
                }
            }
        }
    }

    private Map<String, Object>[] copyCatalog() {
        Map<String, Object>[] newCatalog = new Map[catalog.length];
        for (int i = 0; i < catalog.length; i++) {
            Map<String, Object> entry = catalog[i];
            if (entry != null) {
                newCatalog[i] = new LinkedHashMap<>(entry);
            }
        }
        return newCatalog;
    }

    private static ComputePlatform determineCloudProvider() {
        String computePlatform = System.getProperty(CLOUD_PLATFORM_PROPERTY);
        if (computePlatform != null) {

            try {
                return ComputePlatform.valueOf(computePlatform);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Illegal value specified for [" + CLOUD_PLATFORM_PROPERTY + "]: " + computePlatform);
            }

        }
        boolean isWindows = System.getProperty("os.name")
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
        //Azure - see http://blog.mszcool.com/index.php/2015/04/detecting-if-a-virtual-machine-runs-in-microsoft-azure-linux-windows-to-protect-your-software-when-distributed-via-the-azure-marketplace/
        //IBM - uses cloudfoundry, will have to use that to probe
        // if all else fails not a cloud server that we can tell
        return ComputePlatform.BARE_METAL;
    }

    @SuppressWarnings("MagicNumber")
    private static boolean isGoogleCompute() {
        try {
            final HttpURLConnection con = createConnection(GOOGLE_COMPUTE_METADATA);
            con.setRequestMethod("GET");
            con.setDoOutput(true);
            int responseCode = con.getResponseCode();
            BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            if (con.getHeaderField("Metadata-Flavor") != null &&
                con.getHeaderField("Metadata-Flavor").equalsIgnoreCase("Google")) {
                return true;
            }

        } catch (IOException e) {
            // well not google then
        }
        return false;
    }

    @SuppressWarnings("MagicNumber")
    private static boolean isOracleCloudLinux() {
        if (readFile(ORACLE_CLOUD_ASSET_TAG_FILE).toLowerCase().contains("oraclecloud")) {
            return true;
        }
        return false;
    }

    private static Optional<Process> runWindowsCmd(String cmd) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("cmd.exe", "/c", cmd);
            builder.redirectErrorStream(true);
            builder.directory(new File(System.getProperty("user.home")));
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
            return new String(Files.readAllBytes(Paths.get(path))).trim();
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
        }
        return false;
    }

    private static HttpURLConnection createConnection(String spec) throws IOException {
        final URL url = new URL(spec);
        final HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setReadTimeout(DEFAULT_READ_TIMEOUT);
        con.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
        return con;
    }

    private static boolean isDigitalOcean() {
        try {
            String sysVendor = new String(Files.readAllBytes(Paths.get(DO_SYS_VENDOR_FILE)));
            return "digitalocean".equals(sysVendor.toLowerCase());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Helper class for handling environments and package.
     */
    private static class EnvironmentsAndPackage {
        String aPackage;
        Set<String> enviroments = new LinkedHashSet<>(1);
    }
}
