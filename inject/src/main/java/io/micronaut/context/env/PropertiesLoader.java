package io.micronaut.context.env;

import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.env.graalvm.GraalBuildTimeEnvironment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PropertiesLoader {

    private static final Logger LOG = LoggerFactory.getLogger(GraalBuildTimeEnvironment.class);

    private final String name;
    private final Collection<String> activeNames;
    private final Collection<String> configLocations;
    private final ResourceLoader resourceLoader;
    private ApplicationContextConfiguration configuration;

    private Collection<PropertySourceLoader> propertySourceLoaders;

    public PropertiesLoader(String name, Collection<String> activeNames, Collection<String> configLocations, ResourceLoader resourceLoader) {
        this.name = name;
        this.activeNames = activeNames;
        this.configLocations = configLocations;
        this.resourceLoader = resourceLoader;
    }

    public PropertySourcePropertyResolver read() {
        PropertySourcePropertyResolver propertySourcePropertyResolver = new PropertySourcePropertyResolver();
//        refreshablePropertySources.clear();
        List<PropertySource> propertySources = readPropertySourceList();
//        addDefaultPropertySources(propertySources);
//        String propertySourcesSystemProperty = System.getProperty(Environment.PROPERTY_SOURCES_KEY);
//        if (propertySourcesSystemProperty != null) {
//            propertySources.addAll(readPropertySourceListFromFiles(propertySourcesSystemProperty));
//        }
//        String propertySourcesEnv = readPropertySourceListKeyFromEnvironment();
//        if (propertySourcesEnv != null) {
//            propertySources.addAll(readPropertySourceListFromFiles(propertySourcesEnv));
//        }
//        refreshablePropertySources.addAll(propertySources);

//        propertySources.addAll(this.propertySources.values());
        OrderUtil.sort(propertySources);
        for (PropertySource propertySource : propertySources) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Processing property source: {}", propertySource.getName());
            }
            propertySourcePropertyResolver.processPropertySource(propertySource, propertySource.getConvention());
        }
        return propertySourcePropertyResolver;
    }

    /**
     * Adds default property sources.
     *
     * @param propertySources The list of property sources
     */
    protected void addDefaultPropertySources(List<PropertySource> propertySources) {
//        if (!this.propertySources.containsKey(SystemPropertiesPropertySource.NAME)) {
//            propertySources.add(new SystemPropertiesPropertySource());
//        }
//        if (!this.propertySources.containsKey(EnvironmentPropertySource.NAME) && configuration.isEnvironmentPropertySource()) {
//            List<String> includes = configuration.getEnvironmentVariableIncludes();
//            List<String> excludes = configuration.getEnvironmentVariableExcludes();
//            if (this.names.contains(Environment.KUBERNETES)) {
//                propertySources.add(new KubernetesEnvironmentPropertySource(includes, excludes));
//            } else {
//                propertySources.add(new EnvironmentPropertySource(includes, excludes));
//            }
//        }
    }

    protected List<PropertySource> readPropertySourceList() {
        List<PropertySource> propertySources = new ArrayList<>();
        for (String configLocation : configLocations) {
            ResourceLoader resourceLoader;
            if (configLocation.equals("classpath:/")) {
                resourceLoader = this.resourceLoader;
            } else if (configLocation.startsWith("classpath:")) {
                resourceLoader = this.resourceLoader.forBase(configLocation);
            } else if (configLocation.startsWith("file:")) {
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
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Reading property sources from loader: {}", propertySourceLoader);
                }
                loadPropertySourceFromLoader(name, propertySourceLoader, propertySources, resourceLoader);
            }
        }
    }

    private void loadPropertySourceFromLoader(String name, PropertySourceLoader propertySourceLoader, List<PropertySource> propertySources, ResourceLoader resourceLoader) {
        Optional<PropertySource> defaultPropertySource = propertySourceLoader.load(name, resourceLoader);
        defaultPropertySource.ifPresent(propertySources::add);
        int i = 0;
        for (String activeName : activeNames) {
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

    private Collection<PropertySourceLoader> getPropertySourceLoaders() {
        if (propertySourceLoaders == null) {
            Collection<PropertySourceLoader> allLoaders = new ArrayList<>(10);
            SoftServiceLoader.load(PropertySourceLoader.class).collectAll(allLoaders);
            propertySourceLoaders = allLoaders;
        }
        return propertySourceLoaders;
    }

}
