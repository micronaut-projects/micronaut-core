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

package io.micronaut.discovery.cloud.gcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver;
import io.micronaut.discovery.cloud.NetworkInterface;
import io.micronaut.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
            return Optional.empty();
        }
        if (cachedMetadata != null) {
            cachedMetadata.cached = true;
            return Optional.of(cachedMetadata);
        }

        try {
            int connectionTimeoutMs = (int) configuration.getConnectTimeout().toMillis();
            int readTimeoutMs = (int) configuration.getReadTimeout().toMillis();
            JsonNode projectResultJson = readGcMetadataUrl(new URL(configuration.getProjectMetadataUrl() + "?recursive=true"), connectionTimeoutMs, readTimeoutMs);
            JsonNode instanceMetadataJson = readGcMetadataUrl(new URL(configuration.getMetadataUrl() + "?recursive=true"), connectionTimeoutMs, readTimeoutMs);

            if (instanceMetadataJson != null) {
                GoogleComputeInstanceMetadata instanceMetadata = new GoogleComputeInstanceMetadata();
                instanceMetadata.instanceId = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.ID.getName()).asText();
                instanceMetadata.account = projectResultJson.findValue(GoogleComputeMetadataKeys.PROJECT_ID.getName()).textValue();
                instanceMetadata.availabilityZone = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.ZONE.getName()).textValue();
                instanceMetadata.machineType = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.MACHINE_TYPE.getName()).textValue();
                instanceMetadata.description = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.DESCRIPTION.getName()).textValue();
                instanceMetadata.imageId = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.IMAGE.getName()).textValue();
                instanceMetadata.localHostname = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.HOSTNAME.getName()).textValue();
                instanceMetadata.name = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.NAME.getName()).textValue();
                JsonNode networkInterfaces = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.NETWORK_INTERFACES.getName());
                List<NetworkInterface> interfaces = new ArrayList<NetworkInterface>();
                AtomicReference<Integer> networkCounter = new AtomicReference<>(0);
                networkInterfaces.elements().forEachRemaining(
                    jsonNode -> {
                        GoogleComputeNetworkInterface networkInterface = new GoogleComputeNetworkInterface();
                        networkInterface.setId(networkCounter.toString());
                        if (jsonNode.findValue(GoogleComputeMetadataKeys.ACCESS_CONFIGS.getName()) != null) {
                            JsonNode accessConfigs = jsonNode.findValue(GoogleComputeMetadataKeys.ACCESS_CONFIGS.getName());
                            // we just grab the first one
                            instanceMetadata.publicIpV4 = accessConfigs.get(0).findValue("externalIp").textValue();
                        }
                        if (jsonNode.findValue(GoogleComputeMetadataKeys.IP.getName()) != null) {
                            networkInterface.setIpv4(jsonNode.findValue(GoogleComputeMetadataKeys.IP.getName()).textValue());
                            instanceMetadata.privateIpV4 = jsonNode.findValue(GoogleComputeMetadataKeys.IP.getName()).textValue();
                        }
                        if (jsonNode.findValue(GoogleComputeMetadataKeys.MAC.getName()) != null) {
                            networkInterface.setMac(jsonNode.findValue(GoogleComputeMetadataKeys.MAC.getName()).textValue());
                        }
                        if (jsonNode.findValue(GoogleComputeMetadataKeys.NETWORK.getName()) != null) {
                            networkInterface.setNetwork(jsonNode.findValue(GoogleComputeMetadataKeys.NETWORK.getName()).textValue());
                        }
                        if (jsonNode.findValue(GoogleComputeMetadataKeys.NETMASK.getName()) != null) {
                            networkInterface.setNetmask(jsonNode.findValue(GoogleComputeMetadataKeys.NETMASK.getName()).textValue());
                        }
                        if (jsonNode.findValue(GoogleComputeMetadataKeys.GATEWAY.getName()) != null) {
                            networkInterface.setGateway(jsonNode.findValue(GoogleComputeMetadataKeys.GATEWAY.getName()).textValue());
                        }
                        networkCounter.getAndSet(networkCounter.get() + 1);
                        interfaces.add(networkInterface);

                    });
                instanceMetadata.interfaces = interfaces;
                instanceMetadata.metadata = objectMapper.convertValue(instanceMetadata, Map.class);
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
                LOG.error("Error connecting to" + configuration.getMetadataUrl() + "?recursive=true reading instance metadata", ioe);
            }
        }

        return Optional.empty();
    }

    /**
     * Get instance Metadata JSON.
     *
     * @param url                 The metadata URL
     * @param connectionTimeoutMs connection timeout in millis
     * @param readTimeoutMs       read timeout in millis
     * @return The Metadata JSON
     * @throws IOException Failed or interrupted I/O operations while reading from input stream.
     */
    protected JsonNode readGcMetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {
        URLConnection urlConnection = url.openConnection();

        if (url.getProtocol().equalsIgnoreCase("file")) {
            urlConnection.connect();
            try (InputStream in = urlConnection.getInputStream()) {
                return objectMapper.readTree(in);
            }
        } else {
            HttpURLConnection uc = (HttpURLConnection) urlConnection;

            uc.setConnectTimeout(connectionTimeoutMs);
            uc.setRequestProperty(HEADER_METADATA_FLAVOR, "Google");
            uc.setReadTimeout(readTimeoutMs);
            uc.setRequestMethod(HttpMethod.GET.name());
            uc.setDoOutput(true);
            int responseCode = uc.getResponseCode();
            try (InputStream in = uc.getInputStream()) {
                return objectMapper.readTree(in);
            }
        }
    }
}
