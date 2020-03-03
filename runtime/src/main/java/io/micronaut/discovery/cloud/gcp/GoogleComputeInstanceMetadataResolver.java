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
package io.micronaut.discovery.cloud.gcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver;
import io.micronaut.discovery.cloud.NetworkInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.discovery.cloud.ComputeInstanceMetadataResolverUtils.*;

/**
 * Resolves {@link ComputeInstanceMetadata} for Google Cloud Platform.
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(env = Environment.GOOGLE_COMPUTE)
public class GoogleComputeInstanceMetadataResolver implements ComputeInstanceMetadataResolver {

    /**
     * Constant for Metadata flavor.
     */
    public static final String HEADER_METADATA_FLAVOR = "Metadata-Flavor";

    private static final Logger LOG = LoggerFactory.getLogger(GoogleComputeInstanceMetadataResolver.class);

    private final ObjectMapper objectMapper;
    private final GoogleComputeMetadataConfiguration configuration;
    private GoogleComputeInstanceMetadata cachedMetadata;

    /**
     *
     * @param objectMapper To read and write JSON
     * @param configuration The configuration for computing Google Metadata
     */
    @Inject
    public GoogleComputeInstanceMetadataResolver(ObjectMapper objectMapper, GoogleComputeMetadataConfiguration configuration) {
        this.objectMapper = objectMapper;
        this.configuration = configuration;
    }

    /**
     * Construct with default settings.
     */
    public GoogleComputeInstanceMetadataResolver() {
        this.objectMapper = new ObjectMapper();
        this.configuration = new GoogleComputeMetadataConfiguration();
    }

    @Override
    public Optional<ComputeInstanceMetadata> resolve(Environment environment) {
        if (!configuration.isEnabled()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolving of Google Compute Instance metadata is disabled");
            }
            return Optional.empty();
        }
        if (cachedMetadata != null) {
            cachedMetadata.setCached(true);
            return Optional.of(cachedMetadata);
        }

        GoogleComputeInstanceMetadata instanceMetadata = null;

        try {
            int connectionTimeoutMs = (int) configuration.getConnectTimeout().toMillis();
            int readTimeoutMs = (int) configuration.getReadTimeout().toMillis();
            Map<String, String> requestProperties = new HashMap<>();
            requestProperties.put(HEADER_METADATA_FLAVOR, "Google");
            JsonNode projectResultJson = null;
            try {
                projectResultJson = readMetadataUrl(
                        new URL(configuration.getProjectMetadataUrl() + "?recursive=true"),
                        connectionTimeoutMs,
                        readTimeoutMs,
                        objectMapper,
                        requestProperties);
            } catch (MalformedURLException me) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Google compute project metadataUrl value is invalid!: " + configuration.getProjectMetadataUrl(), me);
                }
            } catch (FileNotFoundException fnfe) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No project metadata found at: " + configuration.getProjectMetadataUrl() + "?recursive=true", fnfe);
                }
            } catch (IOException ioe) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Error connecting to" + configuration.getProjectMetadataUrl() + "?recursive=true reading project metadata. Not a Google environment?", ioe);
                }
            }
            JsonNode instanceMetadataJson = readMetadataUrl(new URL(configuration.getMetadataUrl() + "?recursive=true"), connectionTimeoutMs, readTimeoutMs, objectMapper, requestProperties);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Read compute instance metadata from URL [{}]. Resulting JSON: {}", configuration.getMetadataUrl(), instanceMetadataJson);
            }


            if (environment.getActiveNames().contains(Environment.GAE)) {
                instanceMetadata = new GoogleComputeInstanceMetadata();
                instanceMetadata.setInstanceId(System.getenv("GAE_INSTANCE"));
                instanceMetadata.setAccount(System.getenv("GOOGLE_CLOUD_PROJECT"));
            }

            if (instanceMetadataJson != null) {
                if (instanceMetadata == null) {
                    instanceMetadata = new GoogleComputeInstanceMetadata();
                }
                stringValue(instanceMetadataJson, GoogleComputeMetadataKeys.ID.getName()).ifPresent(instanceMetadata::setInstanceId);
                if (projectResultJson != null) {
                    stringValue(projectResultJson, GoogleComputeMetadataKeys.PROJECT_ID.getName()).ifPresent(instanceMetadata::setAccount);
                } else {
                    stringValue(instanceMetadataJson, GoogleComputeMetadataKeys.PROJECT_ID.getName()).ifPresent(instanceMetadata::setAccount);
                }
                stringValue(instanceMetadataJson, GoogleComputeMetadataKeys.ZONE.getName()).ifPresent(instanceMetadata::setAvailabilityZone);
                stringValue(instanceMetadataJson, GoogleComputeMetadataKeys.MACHINE_TYPE.getName()).ifPresent(instanceMetadata::setMachineType);
                stringValue(instanceMetadataJson, GoogleComputeMetadataKeys.DESCRIPTION.getName()).ifPresent(instanceMetadata::setDescription);
                stringValue(instanceMetadataJson, GoogleComputeMetadataKeys.IMAGE.getName()).ifPresent(instanceMetadata::setImageId);
                stringValue(instanceMetadataJson, GoogleComputeMetadataKeys.HOSTNAME.getName()).ifPresent(instanceMetadata::setLocalHostname);
                stringValue(instanceMetadataJson, GoogleComputeMetadataKeys.NAME.getName()).ifPresent(instanceMetadata::setName);

                JsonNode networkInterfaces = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.NETWORK_INTERFACES.getName());
                if (networkInterfaces != null) {

                    List<NetworkInterface> interfaces = new ArrayList<NetworkInterface>();
                    AtomicInteger networkCounter = new AtomicInteger(0);
                    GoogleComputeInstanceMetadata finalInstanceMetadata = instanceMetadata;
                    networkInterfaces.elements().forEachRemaining(
                            jsonNode -> {
                                GoogleComputeNetworkInterface networkInterface = new GoogleComputeNetworkInterface();
                                networkInterface.setId(String.valueOf(networkCounter.getAndIncrement()));

                                if (jsonNode.findValue(GoogleComputeMetadataKeys.ACCESS_CONFIGS.getName()) != null) {
                                    JsonNode accessConfigs = jsonNode.findValue(GoogleComputeMetadataKeys.ACCESS_CONFIGS.getName());
                                    // we just grab the first one
                                    finalInstanceMetadata.setPublicIpV4(accessConfigs.get(0).findValue("externalIp").textValue());
                                }

                                stringValue(jsonNode, GoogleComputeMetadataKeys.IP.getName()).ifPresent(finalInstanceMetadata::setPrivateIpV4);
                                stringValue(jsonNode, GoogleComputeMetadataKeys.IP.getName()).ifPresent(networkInterface::setIpv4);
                                stringValue(jsonNode, GoogleComputeMetadataKeys.MAC.getName()).ifPresent(networkInterface::setMac);
                                stringValue(jsonNode, GoogleComputeMetadataKeys.NETWORK.getName()).ifPresent(networkInterface::setNetwork);
                                stringValue(jsonNode, GoogleComputeMetadataKeys.NETMASK.getName()).ifPresent(networkInterface::setNetmask);
                                stringValue(jsonNode, GoogleComputeMetadataKeys.GATEWAY.getName()).ifPresent(networkInterface::setGateway);
                                interfaces.add(networkInterface);
                            });
                    instanceMetadata.setInterfaces(interfaces);
                }
                final Map<?, ?> metadata = objectMapper.convertValue(instanceMetadata, Map.class);
                populateMetadata(instanceMetadata, metadata);
                cachedMetadata = instanceMetadata;

                return Optional.of(instanceMetadata);
            }
        } catch (MalformedURLException me) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Google compute metadataUrl value is invalid!: " + configuration.getMetadataUrl(), me);
            }
        } catch (FileNotFoundException fnfe) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No metadata found at: " + configuration.getMetadataUrl() + "?recursive=true", fnfe);
            }
        } catch (IOException ioe) {
            if (LOG.isErrorEnabled()) {
                LOG.debug("Error connecting to" + configuration.getMetadataUrl() + "?recursive=true reading instance metadata", ioe);
            }
        }

        return Optional.ofNullable(instanceMetadata);
    }

    /**
     * Get instance Metadata JSON.
     *
     * @param url                 The metadata URL
     * @param connectionTimeoutMs connection timeout in millis
     * @param readTimeoutMs       read timeout in millis
     * @return The Metadata JSON
     * @throws IOException Failed or interrupted I/O operations while reading from input stream.
     * @deprecated See {@link io.micronaut.discovery.cloud.ComputeInstanceMetadataResolverUtils#readMetadataUrl(URL, int, int, ObjectMapper, Map)}
     */
    @Deprecated
    protected JsonNode readGcMetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {
        return readMetadataUrl(url, connectionTimeoutMs, readTimeoutMs, objectMapper, Collections.emptyMap());
    }

}
