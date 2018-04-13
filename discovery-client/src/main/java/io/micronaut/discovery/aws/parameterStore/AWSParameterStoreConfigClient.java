package io.micronaut.discovery.aws.parameterStore;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.*;
import io.micronaut.configurations.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.*;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.aws.route53.Route53ClientDiscoveryConfiguration;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * @author Rvanderwerf
 */
@Singleton
@Requires(env= Environment.AMAZON_EC2)
@Requires(beans = AWSParameterStoreConfiguration.class)
@Requires(beans = AWSClientConfiguration.class)
public class AWSParameterStoreConfigClient implements ConfigurationClient {


    final AWSClientConfiguration awsConfiguration;
    final AWSParameterStoreConfiguration awsParameterStoreConfiguration;
    final Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration;

    AWSSimpleSystemsManagement client;
    private ExecutorService executionService;

    protected static final Logger LOG = LoggerFactory.getLogger(AWSParameterStoreConfiguration.class);
    @Value("${" + ApplicationConfiguration.APPLICATION_NAME + "}") String applicationName;

    AWSParameterStoreConfigClient(AWSClientConfiguration awsConfiguration, AWSParameterStoreConfiguration awsParameterStoreConfiguration, Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration ) {
        this.awsConfiguration = awsConfiguration;
        this.awsParameterStoreConfiguration = awsParameterStoreConfiguration;

        try {
            this.client = AWSSimpleSystemsManagementClientBuilder.standard().withClientConfiguration(awsConfiguration.clientConfiguration).build();
        } catch (SdkClientException sce) {
            LOG.warn("Error creating Simple Systems Management client - check your credentials");
        }

        this.route53ClientDiscoveryConfiguration = route53ClientDiscoveryConfiguration;
    }


    /**
     * Property sources are expected to be set up in this way:
     * \ configuration \ micronaut \ environment name \ app name \
     * If you want to change the base \configuration\micronaut set the property aws.systemManager.parameterStore.rootHierarchyPath
     *
     * @param environment The environment
     * @return
     */
    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        if (!awsParameterStoreConfiguration.isEnabled()) {
            return Flowable.empty();
        }
        Set<String> activeNames = environment.getActiveNames();
        Optional<String> serviceId = route53ClientDiscoveryConfiguration.getServiceId();


        String path = awsParameterStoreConfiguration.getRootHierarchyPath();
        if (!path.endsWith("/")) {
            path += "/";
        }

        String pathPrefix = path.substring(1);
        String commonConfigPath = path + Environment.DEFAULT_NAME;
        String commonPrefix = commonConfigPath.substring(1);
        final boolean hasApplicationSpecificConfig = serviceId.isPresent();
        String applicationSpecificPath = hasApplicationSpecificConfig ? path + serviceId.get() : null;
        String applicationPrefix = hasApplicationSpecificConfig ? applicationSpecificPath.substring(1) : null;

        Flowable<List<Parameter>> configurationValues = Flowable.fromPublisher(getPlainTextHierarchy(commonConfigPath, false));

        if (hasApplicationSpecificConfig) {
            configurationValues = Flowable.concat(
                    configurationValues,
                    Flowable.fromPublisher(getPlainTextHierarchy(applicationSpecificPath, false)));
        }
        if (activeNames!=null && activeNames.size() > 0) {
            // look for the environment configs since we can't wildcard partial paths on aws
            for (String activeName : activeNames) {
                String environmentSpecificPath = commonConfigPath+","+activeName;
                configurationValues = Flowable.concat(
                        configurationValues,
                        Flowable.fromPublisher(getPlainTextHierarchy(environmentSpecificPath, false)));

            }

        }

        Flowable<PropertySource> propertySourceFlowable = configurationValues.flatMap(keyValues -> Flowable.create(emitter -> {
            if (CollectionUtils.isEmpty(keyValues)) {
                emitter.onComplete();
            } else {
                Map<String, Map<String, Object>> propertySources = new HashMap<>();

                for (Parameter keyValue : keyValues) {
                    String key = keyValue.getName();
                    String value = keyValue.getValue();
                    boolean isFolder = key.endsWith("/") && value == null;
                    boolean isCommonConfigKey = key.substring(1).startsWith(commonPrefix);
                    boolean isApplicationSpecificConfigKey = hasApplicationSpecificConfig && key.startsWith(applicationPrefix);
                    boolean validKey = isCommonConfigKey || isApplicationSpecificConfigKey;
                    if (!isFolder && validKey) {

                        String fullName = key.substring(pathPrefix.length()+1);
                        if (!fullName.contains("/")) {
                            Set<String> propertySourceNames = null;
                            propertySourceNames = calcPropertySourceNames(fullName, activeNames);
                            String lookupKey = key;
                            if (!lookupKey.startsWith("/")) {
                                lookupKey = "/"+lookupKey;
                            }
                            Flowable<List<Parameter>> parameters = Flowable.fromPublisher(getPlainTextHierarchy(lookupKey, true));

                            Flowable<Map<String, Object>> properties = Flowable.fromPublisher(convertParameterHierarchyToMap(parameters.blockingFirst()));
                            for (String propertySourceName : propertySourceNames) {

                                Map<String, Object> values = propertySources.computeIfAbsent(propertySourceName, s -> new LinkedHashMap<>());
                                values.putAll(properties.blockingFirst());
                            }

                        }
                    }

                }


                for (Map.Entry<String, Map<String, Object>> entry : propertySources.entrySet()) {
                    String name = entry.getKey();
                    int priority = EnvironmentPropertySource.POSITION + (name.endsWith("]") ? 150 : 100);
                    if (hasApplicationSpecificConfig && serviceId.isPresent() && name.startsWith(serviceId.get())) {
                        priority += 10;
                    }
                    emitter.onNext(PropertySource.of(Route53ClientDiscoveryConfiguration.SERVICE_ID + '-' + name, entry.getValue(), priority));
                }
                emitter.onComplete();
            }

        }, BackpressureStrategy.ERROR));

        propertySourceFlowable = propertySourceFlowable.onErrorResumeNext(throwable -> {
            if (throwable instanceof ConfigurationException) {
                return Flowable.error(throwable);
            } else {
                return Flowable.error(new ConfigurationException("Error reading distributed configuration from AWS Parameter Store: " + throwable.getMessage(), throwable));
            }
        });
        if (executionService != null) {
            return propertySourceFlowable.subscribeOn(io.reactivex.schedulers.Schedulers.from(
                    executionService
            ));
        } else {
            return propertySourceFlowable;
        }



        //return Flowable.empty();
    }

    @Override
    public String getDescription() {
        return "AWS Parameter Store";
    }

    String getSecureValue(String key) throws ParameterNotFoundException {
        GetParameterRequest getRequest = new GetParameterRequest().withWithDecryption(true).withName(key);
        GetParameterResult parameterResult = client.getParameter(getRequest);
        if (parameterResult.getParameter()!=null) {
            return parameterResult.getParameter().getValue();
        }
        throw new ParameterNotFoundException("Parameter not found");
    }

    Parameter getUnsecureValue(String key) {
        GetParameterRequest getRequest = new GetParameterRequest().withWithDecryption(false).withName(key);
        GetParameterResult parameterResult = client.getParameter(getRequest);
        if (parameterResult.getParameter()!=null) {
            return parameterResult.getParameter();
        }
        throw new ParameterNotFoundException("Parameter not found");
    }

    List<Parameter> getSecureHierarchy(String path) {
        GetParametersByPathRequest getRequest = new GetParametersByPathRequest().withWithDecryption(true).withPath(path).withRecursive(true);
        GetParametersByPathResult parameterResult = client.getParametersByPath(getRequest);

        if (parameterResult.getParameters()!=null) {
            return parameterResult.getParameters();
        }

        throw new ParameterNotFoundException("Parameter not found");




    }

    Publisher<List<Parameter>> getPlainTextHierarchy(String path, Boolean recursive) {
        GetParametersByPathRequest getRequest = new GetParametersByPathRequest().withWithDecryption(false).withPath(path).withRecursive(recursive);
        GetParametersByPathResult parameterResult = client.getParametersByPath(getRequest);

        if (parameterResult.getParameters()!=null) {
            return Publishers.just(parameterResult.getParameters());
        }

        throw new ParameterNotFoundException("Parameter not found");

    }

    private String resolvePropertySourceName(String rootName, String fileName, Set<String> activeNames) {
        String propertySourceName = null;
        if (fileName.startsWith(rootName)) {
            String envString = fileName.substring(rootName.length());
            if(StringUtils.isEmpty(envString)) {
                propertySourceName = rootName;
            }
            else if(envString.startsWith("-")) {
                String env = envString.substring(1);
                if(activeNames.contains(env)) {
                    propertySourceName = rootName + '['+ env + ']';
                }
            }
        }
        return propertySourceName;
    }

    private Set<String> resolvePropertySourceNames(String finalPath, String key, Set<String> activeNames) {
        Set<String> propertySourceNames = null;
        String prefix = key.substring(finalPath.length());
        int i = prefix.indexOf('/');
        if (i > -1) {
            prefix = prefix.substring(0, i);
            propertySourceNames = calcPropertySourceNames(prefix, activeNames);
            if (propertySourceNames == null) return null;
        }
        return propertySourceNames;
    }


    private Set<String> calcPropertySourceNames(String prefix, Set<String> activeNames) {
        Set<String> propertySourceNames;
        if (prefix.indexOf(',') > -1) {

            String[] tokens = prefix.split(",");
            if (tokens.length == 1) {
                propertySourceNames = Collections.singleton(tokens[0]);
            } else {
                String name = tokens[0];
                Set<String> newSet = new HashSet<>(tokens.length - 1);
                for (int j = 1; j < tokens.length; j++) {
                    String envName = tokens[j];
                    if (!activeNames.contains(envName)) {
                        return Collections.emptySet();
                    }
                    newSet.add(name + '[' + envName + ']');
                }
                propertySourceNames = newSet;
            }
        } else {
            propertySourceNames = Collections.singleton(prefix);
        }
        return propertySourceNames;
    }

    @Inject
    void setExecutionService(@Named(TaskExecutors.IO) @Nullable ExecutorService executionService) {
        if (executionService != null) {
            this.executionService = executionService;
        }
    }

    public Publisher<Map<String,Object>> convertParameterHierarchyToMap(List<Parameter> params) {
        Map output = new HashMap<String,Object>();
        for (Parameter param : params) {
            switch (param.getType()) {
                case "StringList":
                    // command delimited list back into a set/list and exvalues value to be key=value,key=value
                    String[] items = param.getValue().split(",");
                    for (String item : items) {
                        // now split to key value
                        String[] keyValue = item.split("=");
                        output.put(keyValue[0],keyValue[1]);

                    }
                    break;

                case "SecureString":
                  // TODO reach out to KMS to decrypt
                break;

                default:
                case "String":
                    String[] keyValue = param.getValue().split("=");
                    output.put(keyValue[0],keyValue[1]);
                    break;
            }
        }
        return Publishers.just(output);

    }


}


