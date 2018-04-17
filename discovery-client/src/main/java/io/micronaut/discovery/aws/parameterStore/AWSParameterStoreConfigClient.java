package io.micronaut.discovery.aws.parameterStore;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsyncClient;
import com.amazonaws.services.simplesystemsmanagement.model.*;
import io.micronaut.configurations.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.*;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.aws.route53.Route53ClientDiscoveryConfiguration;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author Rvanderwerf
 */
@Singleton
@Requires(env= Environment.AMAZON_EC2)
@Requires(beans = AWSParameterStoreConfiguration.class)
//@Requires(beans = AWSClientConfiguration.class)
public class AWSParameterStoreConfigClient implements ConfigurationClient {


    final AWSClientConfiguration awsConfiguration;
    final AWSParameterStoreConfiguration awsParameterStoreConfiguration;
    final Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration;
    AWSSimpleSystemsManagementAsync client;
    private ExecutorService executionService;

    protected static final Logger LOG = LoggerFactory.getLogger(AWSParameterStoreConfiguration.class);
    @Value("${" + ApplicationConfiguration.APPLICATION_NAME + "}") String applicationName;

    AWSParameterStoreConfigClient(AWSClientConfiguration awsConfiguration, AWSParameterStoreConfiguration awsParameterStoreConfiguration, Route53ClientDiscoveryConfiguration route53ClientDiscoveryConfiguration) {
        this.awsConfiguration = awsConfiguration;
        this.awsParameterStoreConfiguration = awsParameterStoreConfiguration;
        //this.awsSystemManagementClientFactory = awsSystemManagementClientFactory;

        try {
            //TODO convert to async client
            //AWSSystemManagementClientFactory factory = new AWSSystemManagementClientFactory(awsParameterStoreConfiguration);
            //this.client =  factory.awsSimpleSystemsManagementAsyncClient();
            //this.client = AWSSimpleSystemsManagementClientBuilder.standard().withClientConfiguration(awsConfiguration.clientConfiguration).build();
            this.client = AWSSimpleSystemsManagementAsyncClient.asyncBuilder().withClientConfiguration(awsConfiguration.clientConfiguration).build();

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

        Flowable<GetParametersByPathResult> configurationValues = Flowable.fromPublisher(getHierarchy(commonConfigPath, false));

        if (hasApplicationSpecificConfig) {
            configurationValues = Flowable.concat(
                    configurationValues,
                    Flowable.fromPublisher(getHierarchy(applicationSpecificPath, false)));
        }
        if (activeNames!=null && activeNames.size() > 0) {
            // look for the environment configs since we can't wildcard partial paths on aws
            for (String activeName : activeNames) {
                String environmentSpecificPath = commonConfigPath+","+activeName;
                configurationValues = Flowable.concat(
                        configurationValues,
                        Flowable.fromPublisher(getHierarchy(environmentSpecificPath, false)));

            }

        }

        Flowable<PropertySource> propertySourceFlowable = configurationValues.flatMap(keyValues -> Flowable.create(emitter -> {
            if (CollectionUtils.isEmpty(keyValues.getParameters())) {
                emitter.onComplete();
            } else {
                Map<String, Map<String, Object>> propertySources = new HashMap<>();

                for (Parameter keyValue : keyValues.getParameters()) {
                    String key = keyValue.getName();
                    String value = keyValue.getValue();
                    boolean isFolder = key.endsWith("/") && value == null;
                    boolean isCommonConfigKey = key.substring(1).startsWith(commonPrefix);
                    boolean isApplicationSpecificConfigKey = hasApplicationSpecificConfig && key.startsWith(applicationPrefix);
                    boolean validKey = isCommonConfigKey || isApplicationSpecificConfigKey;
                    if (!isFolder && validKey) {

                        String fullName = key.substring(pathPrefix.length()+1);
                        if (!fullName.contains("/")) {
                            Set<String> propertySourceNames = calcPropertySourceNames(fullName, activeNames);
                            String lookupKey = key;
                            if (!lookupKey.startsWith("/")) {
                                lookupKey = "/"+lookupKey;
                            }
                            Flowable<GetParametersByPathResult> parameters = Flowable.fromPublisher(getHierarchy(lookupKey, true));

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

    }

    @Override
    public String getDescription() {
        return "AWS Parameter Store";
    }

    Publisher<GetParametersByPathResult> getHierarchy(String path, Boolean recursive) {

        GetParametersByPathRequest getRequest = new GetParametersByPathRequest().withWithDecryption(awsParameterStoreConfiguration.useSecureParameters).withPath(path).withRecursive(recursive);

        Future<GetParametersByPathResult> future = client.getParametersByPathAsync(getRequest);

        Flowable<GetParametersByPathResult> invokeFlowable = Flowable.fromFuture(future,Schedulers.io());


        invokeFlowable = invokeFlowable.onErrorResumeNext(throwable -> {
            if (throwable instanceof SdkClientException) {
                return Flowable.error(throwable);
            } else {
                return Flowable.error(new ConfigurationException("Error reading distributed configuration from AWS Parameter Store: " + throwable.getMessage(), throwable));
            }
        });
        return invokeFlowable;

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

    public Publisher<Map<String,Object>> convertParameterHierarchyToMap(GetParametersByPathResult result) {
        List<Parameter> params = result.getParameters();
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
                    // if decrypt is set to true on request KMS is supposed to decrypt these for us otherwise we get
                    // back an encoded encrypted string of gobbly gook. It uses the default account key unless
                    // one is specified in the config
                    String[] keyValue = param.getValue().split("=");
                    output.put(keyValue[0],keyValue[1]);

                break;

                default:
                case "String":
                    String[] keyVal = param.getValue().split("=");
                    output.put(keyVal[0],keyVal[1]);
                    break;
            }
        }
        return Publishers.just(output);

    }


}


