/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.context.converters.StringArrayToClassArrayConverter;
import io.micronaut.context.converters.StringToClassConverter;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    //private static final String EC2_LINUX_HYPERVISOR_FILE = "/sys/hypervisor/uuid";
    private static final String EC2_LINUX_HYPERVISOR_FILE = "/tmp/uuid";
    private static final String EC2_WINDOWS_HYPERVISOR_CMD = "wmic path win32_computersystemproduct get uuid";
    private static final String PROPERTY_SOURCES_KEY = "micronaut.config.files";
    private static final String FILE_SEPARATOR = ",";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultEnvironment.class);
    private static final String K8S_ENV = "KUBERNETES_SERVICE_HOST";
    private static final String PCF_ENV = "VCAP_SERVICES";
    private static final String HEROKU_DYNO = "DYNO";

    protected final ClassPathResourceLoader resourceLoader;

    private EnvironmentsAndPackage environmentsAndPackage;

    private final Set<String> names;
    private final ClassLoader classLoader;
    private final Collection<String> packages = new ConcurrentLinkedQueue<>();
    private final ClassPathAnnotationScanner annotationScanner;
    private Collection<String> configurationIncludes = new HashSet<>();
    private Collection<String> configurationExcludes = new HashSet<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Collection<PropertySourceLoader> propertySourceLoaderList;
    private final Map<String, PropertySourceLoader> loaderByFormatMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> presenceCache = new ConcurrentHashMap<>();
    private final AtomicBoolean reading = new AtomicBoolean(false);

    /**
     * @param classLoader The class loader
     * @param names       The names
     */
    public DefaultEnvironment(ClassLoader classLoader, String... names) {
        this(classLoader, ConversionService.SHARED, names);
    }

    /**
     * @param names The names
     */
    public DefaultEnvironment(String... names) {
        this(DefaultEnvironment.class.getClassLoader(), ConversionService.SHARED, names);
    }

    /**
     * @param classLoader       The class loader
     * @param conversionService The conversion service
     * @param names             The names
     */
    public DefaultEnvironment(ClassLoader classLoader, ConversionService conversionService, String... names) {
        this(ClassPathResourceLoader.defaultLoader(classLoader), conversionService, names);
    }

    /**
     * @param resourceLoader    The resource loader
     * @param conversionService The conversion service
     * @param names             The names
     */
    @SuppressWarnings("MagicNumber")
    public DefaultEnvironment(ClassPathResourceLoader resourceLoader, ConversionService conversionService, String... names) {
        super(conversionService);
        Set<String> specifiedNames = new HashSet<>(3);
        specifiedNames.addAll(CollectionUtils.setOf(names));

        if (!specifiedNames.contains(Environment.FUNCTION) && shouldDeduceEnvironments()) {
            EnvironmentsAndPackage environmentsAndPackage = getEnvironmentsAndPackage();
            specifiedNames.addAll(environmentsAndPackage.enviroments);
            String aPackage = environmentsAndPackage.aPackage;
            if (aPackage != null) {
                packages.add(aPackage);
            }
        }

        this.classLoader = resourceLoader.getClassLoader();
        this.names = specifiedNames;
        conversionService.addConverter(
            CharSequence.class, Class.class, new StringToClassConverter(classLoader)
        );
        conversionService.addConverter(
            Object[].class, Class[].class, new StringArrayToClassArrayConverter(conversionService)
        );
        this.resourceLoader = resourceLoader;
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
            processPropertySource(propertySource, PropertySource.PropertyConvention.JAVA_PROPERTIES);
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
        synchronized (catalog) {
            for (int i = 0; i < catalog.length; i++) {
                catalog[i] = null;
            }
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
        return true;
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
        List<PropertySource> propertySources = readPropertySourceList(name);
        propertySources.addAll(this.propertySources.values());
        propertySources.addAll(readPropertySourceListFromFiles(System.getProperty(PROPERTY_SOURCES_KEY)));
        propertySources.addAll(readPropertySourceListFromFiles(
            readPropertySourceListKeyFromEnvironment())
        );
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
        return System.getenv(StringUtils.convertDotToUnderscore(PROPERTY_SOURCES_KEY));
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
                list.forEach(filePath -> {
                    if (!propertySourceLoaders.isEmpty()) {
                        String extension = NameUtils.extension(filePath);
                        String fileName = NameUtils.filename(filePath);
                        Optional<PropertySourceLoader> propertySourceLoader = Optional.ofNullable(loaderByFormatMap.get(extension));
                        if (propertySourceLoader.isPresent()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Reading property sources from loader: {}", propertySourceLoader);
                            }
                            readPropertySourceFromLoader(fileName, filePath, propertySourceLoader.get(), propertySources);
                        } else {
                            throw new ConfigurationException("Unsupported properties file format: " + fileName);
                        }
                    }
                });
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
        if (!this.propertySources.containsKey(SystemPropertiesPropertySource.NAME)) {
            propertySources.add(new SystemPropertiesPropertySource());
        }
        if (!this.propertySources.containsKey(EnvironmentPropertySource.NAME)) {
            propertySources.add(new EnvironmentPropertySource());
        }
        return propertySources;
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
        Optional<PropertySource> defaultPropertySource = propertySourceLoader.load(name, this, null);
        defaultPropertySource.ifPresent(propertySources::add);
        Set<String> activeNames = getActiveNames();
        for (String activeName : activeNames) {
            Optional<PropertySource> propertySource = propertySourceLoader.load(name, this, activeName);
            propertySource.ifPresent(propertySources::add);
        }
    }

    /**
     * Read the property source.
     *
     * @param fileName             Name of the file to be used as property source name
     * @param filePath             Absolute file path
     * @param propertySourceLoader The appropriate property source loader
     * @param propertySources      List of property sources to add to
     * @throws ConfigurationException If unable to find the appropriate property soruce loader for the given file
     */
    private void readPropertySourceFromLoader(String fileName, String filePath, PropertySourceLoader propertySourceLoader, List<PropertySource> propertySources) throws ConfigurationException {
        ResourceResolver resourceResolver = new ResourceResolver();
        Optional<ResourceLoader> resourceLoader = resourceResolver.getSupportingLoader(filePath);
        ResourceLoader loader = resourceLoader.orElse(FileSystemResourceLoader.defaultLoader());
        try {
            Optional<InputStream> inputStream = loader.getResourceAsStream(filePath);
            if (inputStream.isPresent()) {
                Map<String, Object> properties = propertySourceLoader.read(fileName, inputStream.get());
                propertySources.add(PropertySource.of(properties));
            }
        } catch (IOException e) {
            throw new ConfigurationException("Unsupported properties file: " + fileName);
        }
    }

    private EnvironmentsAndPackage getEnvironmentsAndPackage() {
        EnvironmentsAndPackage environmentsAndPackage = this.environmentsAndPackage;
        if (environmentsAndPackage == null) {
            synchronized (EnvironmentsAndPackage.class) { // double check
                environmentsAndPackage = this.environmentsAndPackage;
                if (environmentsAndPackage == null) {
                    environmentsAndPackage = deduceEnvironments();
                    this.environmentsAndPackage = environmentsAndPackage;
                }
            }
        }
        return environmentsAndPackage;
    }

    private static EnvironmentsAndPackage deduceEnvironments() {


        EnvironmentsAndPackage environmentsAndPackage = new EnvironmentsAndPackage();
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        ListIterator<StackTraceElement> stackTraceIterator = Arrays.asList(stackTrace).listIterator();
        Set<String> environments = environmentsAndPackage.enviroments;

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

            if (Stream.of("org.spockframework", "org.junit").anyMatch(className::startsWith)) {
                environments.add(TEST);
            }

            if (className.startsWith("com.android")) {
                environments.add(ANDROID);
            }
        }

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
            }

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
                    case OTHER:
                        // do nothing here
                        break;
                    default:
                        // no-op
                }
            }
        }

        Stream.of(System.getProperty(ENVIRONMENTS_PROPERTY),
            System.getenv(ENVIRONMENTS_ENV))
            .filter(StringUtils::isNotEmpty)
            .flatMap(s -> Arrays.stream(s.split(",")))
            .map(String::trim)
            .forEach(environments::add);

        if (LOG.isInfoEnabled() && !environments.isEmpty()) {
            LOG.info("Established active environments: {}", environments);
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
        if (System.getenv("TRAVIS") != null) {
            return ComputePlatform.OTHER;
        }
        
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

        if (isWindows) {
            if (isEC2Windows()) {
                return ComputePlatform.AMAZON_EC2;
            }
            if (isGoogleCompute()) {
                return ComputePlatform.GOOGLE_COMPUTE;
            }

        } else {
            // can just read from the file
            if (isEC2Linux()) {
                return ComputePlatform.AMAZON_EC2;
            }
        }
        // let's try google
        if (isGoogleCompute()) {
            return ComputePlatform.GOOGLE_COMPUTE;
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
            URL url = new URL("http://metadata.google.internal");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setReadTimeout(500);
            con.setConnectTimeout(500);
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

    private static boolean isEC2Linux() {
        try {
            String contents = new String(Files.readAllBytes(Paths.get(EC2_LINUX_HYPERVISOR_FILE)));
            if (contents.startsWith("ec2")) {
                return true;
            }
        } catch (IOException e) {
            // well that's not it!
        }
        return false;
    }

    private static boolean isEC2Windows() {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("cmd.exe", "/c", EC2_WINDOWS_HYPERVISOR_CMD);
            builder.redirectErrorStream(true);
            builder.directory(new File(System.getProperty("user.home")));
            Process process = builder.start();

            //Read out dir output
            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            StringBuilder stdout = new StringBuilder();
            while ((line = br.readLine()) != null) {
                stdout.append(line);
            }

            //Wait to get exit value
            try {
                int exitValue = process.waitFor();
                if (exitValue == 0 && stdout.toString().startsWith("EC2")) {
                    return true;
                }
            } catch (InterruptedException e) {
                // test negative
            }

        } catch (IOException e) {

        }
        return false;
    }

    /**
     * Helper class for handling environments and package.
     */
    private static class EnvironmentsAndPackage {
        String aPackage;
        Set<String> enviroments = new HashSet<>(1);
    }
}
