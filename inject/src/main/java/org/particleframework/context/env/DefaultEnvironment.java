/*
 * Copyright 2017 original authors
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
package org.particleframework.context.env;

import org.particleframework.context.converters.StringArrayToClassArrayConverter;
import org.particleframework.context.converters.StringToClassConverter;
import org.particleframework.context.exceptions.ConfigurationException;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.io.ResourceLoader;
import org.particleframework.core.io.scan.CachingClassPathAnnotationScanner;
import org.particleframework.core.io.scan.ClassPathAnnotationScanner;
import org.particleframework.core.io.service.ServiceDefinition;
import org.particleframework.core.io.service.SoftServiceLoader;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.order.OrderUtil;
import org.particleframework.core.util.CollectionUtils;
import org.particleframework.inject.BeanConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.annotation.Annotation;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
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

    private static EnvironmentsAndPackage environmentsAndPackage;
    //private static final String EC2_LINUX_HYPERVISOR_FILE = "/sys/hypervisor/uuid";
    private static final String EC2_LINUX_HYPERVISOR_FILE = "/tmp/uuid";
    private static final String EC2_WINDOWS_HYPERVISOR_CMD = "wmic path win32_computersystemproduct get uuid";
    private static final Logger LOG  = LoggerFactory.getLogger(DefaultEnvironment.class);

    private final Set<String> names;
    private final ClassLoader classLoader;
    protected final ResourceLoader resourceLoader;
    private final Collection<String> packages = new ConcurrentLinkedQueue<>();
    private final ClassPathAnnotationScanner annotationScanner;
    private Collection<String> configurationIncludes = new HashSet<>();
    private Collection<String> configurationExcludes = new HashSet<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AtomicBoolean reading = new AtomicBoolean(false);

    public DefaultEnvironment(ClassLoader classLoader, String... names) {
        this(classLoader, ConversionService.SHARED, names);
    }

    public DefaultEnvironment(String... names) {
        this(DefaultEnvironment.class.getClassLoader(), ConversionService.SHARED, names);
    }

    public DefaultEnvironment(ClassLoader classLoader, ConversionService conversionService, String... names) {
        this(ResourceLoader.of(classLoader), conversionService, names);
    }

    public DefaultEnvironment(ResourceLoader resourceLoader, ConversionService conversionService, String... names) {
        super(conversionService);
        Set<String> specifiedNames = new HashSet<>(3);
        specifiedNames.addAll(CollectionUtils.setOf(names));
        EnvironmentsAndPackage environmentsAndPackage = getEnvironmentsAndPackage();
        specifiedNames.addAll(environmentsAndPackage.enviroments);
        Package aPackage = environmentsAndPackage.aPackage;
        if(aPackage != null) {
            packages.add(aPackage.getName());
        }
        this.classLoader = resourceLoader.getClassLoader();
        this.names = specifiedNames;
        conversionService.addConverter(
                CharSequence.class, Class.class, new StringToClassConverter(classLoader)
        );
        conversionService.addConverter(
                Object[].class, Class[].class, new StringArrayToClassArrayConverter(classLoader)
        );
        this.resourceLoader = resourceLoader;
        this.annotationScanner = createAnnotationScanner(classLoader);
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
        propertySources.put(propertySource.getName(),propertySource);
        if(isRunning() && !reading.get()) {
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
    public Collection<PropertySource> getPropertySources() {
        return Collections.unmodifiableCollection(this.propertySources.values());
    }

    @Override
    public Environment start() {
        if(running.compareAndSet(false, true)) {
            if(reading.compareAndSet(false, true)) {
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

    protected String getPropertySourceRootName() {
        return Environment.DEFAULT_NAME;
    }

    protected void readPropertySources(String name) {
        List<PropertySource> propertySources = readPropertySourceList(name);
        OrderUtil.sort(propertySources);
        for (PropertySource propertySource : propertySources) {
            processPropertySource(propertySource, propertySource.getConvention());
        }
    }

    protected List<PropertySource> readPropertySourceList(String name) {
        List<PropertySource> propertySources = new ArrayList<>(this.propertySources.values());
        SoftServiceLoader<PropertySourceLoader> propertySourceLoaders = readPropertySourceLoaders();
        boolean hasLoaders = false;
        for (ServiceDefinition<PropertySourceLoader> loader : propertySourceLoaders) {
            hasLoaders = true;
            if(loader.isPresent()) {
                PropertySourceLoader propertySourceLoader = loader.load();
                loadPropertySourceFromLoader(name, propertySourceLoader, propertySources);
            }
        }
        if(!hasLoaders) {
            loadPropertySourceFromLoader(name, new PropertiesPropertySourceLoader(), propertySources);
        }
        if(!this.propertySources.containsKey(SystemPropertiesPropertySource.NAME)) {
            propertySources.add(new SystemPropertiesPropertySource());
        }
        if(!this.propertySources.containsKey(EnvironmentPropertySource.NAME)) {
            propertySources.add(new EnvironmentPropertySource());
        }
        return propertySources;
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

    protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
        return SoftServiceLoader.load(PropertySourceLoader.class);
    }

    private static EnvironmentsAndPackage getEnvironmentsAndPackage() {
        EnvironmentsAndPackage environmentsAndPackage = DefaultEnvironment.environmentsAndPackage;
        if (environmentsAndPackage == null) {
            synchronized (EnvironmentsAndPackage.class) { // double check
                environmentsAndPackage = DefaultEnvironment.environmentsAndPackage;
                if (environmentsAndPackage == null) {
                    DefaultEnvironment.environmentsAndPackage = environmentsAndPackage = deduceEnvironments();
                }
            }
        }
        return environmentsAndPackage;
    }

    private static EnvironmentsAndPackage deduceEnvironments() {
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
        ComputePlatform computePlatform = determineCloudProvider();
        if (computePlatform != null) {
            switch (computePlatform) {
                case GOOGLE_COMPUTE:
                    //instantiate bean for GC metadata discovery
                    environmentsAndPackage.enviroments.add(Environment.GOOGLE_COMPUTE);
                    break;
                case AMAZON_EC2:
                    //instantiate bean for ec2 metadata discovery
                    environmentsAndPackage.enviroments.add(Environment.AMAZON_EC2);
                    break;
                case AZURE:
                    // not implemented
                    environmentsAndPackage.enviroments.add(Environment.AZURE);
                    break;
                case IBM:
                    // not implemented
                    environmentsAndPackage.enviroments.add(Environment.IBM);
                    break;
                case OTHER:
                    // do nothing here
                    break;
            }

        }
        if(LOG.isInfoEnabled()) {
            LOG.info("Established active environments: {}", environmentsAndPackage.enviroments);
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


    private static ComputePlatform determineCloudProvider() {

        String computePlatform = System.getProperty(Environment.CLOUD_PLATFORM_PROPERTY);
        if (computePlatform!=null) {

            try {
                return ComputePlatform.valueOf(computePlatform);
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Illegal value specified for ["+Environment.CLOUD_PLATFORM_PROPERTY+"]: " + computePlatform);
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



    private static boolean isGoogleCompute() {
        try {
            URL url = new URL("http://metadata.google.internal");
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
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
            if (con.getHeaderField("Metadata-Flavor")!=null &&
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

    private static class EnvironmentsAndPackage {
        Package aPackage;
        Set<String> enviroments = new HashSet<>(1);
    }

}



