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

package io.micronaut.discovery.cloud.aws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.io.IOUtils;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves {@link ComputeInstanceMetadata} for Amazon EC2.
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(env = Environment.AMAZON_EC2)
public class AmazonComputeInstanceMetadataResolver implements ComputeInstanceMetadataResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AmazonComputeInstanceMetadataResolver.class);
    private static final int READ_TIMEOUT_IN_MILLS = 5000;
    private static final int CONNECTION_TIMEOUT_IN_MILLS = 5000;

    private final ObjectMapper objectMapper;
    private final AmazonMetadataConfiguration configuration;
    private AmazonEC2InstanceMetadata cachedMetadata;

    /**
     * Create a new instance to resolve {@link ComputeInstanceMetadata} for Amazon EC2.
     *
     * @param objectMapper To convert AWS EC2 metadata information into Map
     * @param configuration AWS Metadata configuration
     */
    @Inject
    public AmazonComputeInstanceMetadataResolver(ObjectMapper objectMapper, AmazonMetadataConfiguration configuration) {
        this.objectMapper = objectMapper;
        this.configuration = configuration;
    }

    /**
     * Create a new instance to resolve {@link ComputeInstanceMetadata} for Amazon EC2 with default configurations.
     */
    public AmazonComputeInstanceMetadataResolver() {
        this.objectMapper = new ObjectMapper();
        this.configuration = new AmazonMetadataConfiguration();
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
        AmazonEC2InstanceMetadata ec2InstanceMetadata = new AmazonEC2InstanceMetadata();
        try {
            String ec2InstanceIdentityDocURL = configuration.getInstanceDocumentUrl();
            String ec2InstanceMetadataURL = configuration.getMetadataUrl();
            JsonNode metadataJson = readEc2MetadataJson(new URL(ec2InstanceIdentityDocURL), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
            if (metadataJson != null) {
                ec2InstanceMetadata.account = metadataJson.findValue(EC2MetadataKeys.accountId.name()).textValue();
                ec2InstanceMetadata.availabilityZone = metadataJson.findValue(EC2MetadataKeys.availabilityZone.name()).textValue();
                ec2InstanceMetadata.instanceId = metadataJson.findValue(EC2MetadataKeys.instanceId.name()).textValue();
                ec2InstanceMetadata.machineType = metadataJson.findValue(EC2MetadataKeys.instanceType.name()).textValue();
                ec2InstanceMetadata.region = metadataJson.findValue(EC2MetadataKeys.region.name()).textValue();
                ec2InstanceMetadata.privateIpV4 = metadataJson.findValue("privateIp").textValue();
                ec2InstanceMetadata.imageId = metadataJson.findValue("imageId").textValue();
            }
            try {
                ec2InstanceMetadata.localHostname = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + EC2MetadataKeys.localHostname.getName()), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
            } catch (IOException e) {
                LOG.error("Error getting local hostname from url:" + ec2InstanceMetadataURL + EC2MetadataKeys.localHostname.name(), e);
            }
            try {
                ec2InstanceMetadata.publicHostname = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + EC2MetadataKeys.publicHostname.getName()), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
            } catch (IOException e) {
                LOG.error("error getting public host name from:" + ec2InstanceMetadataURL + EC2MetadataKeys.publicHostname.name(), e);
            }
            // build up network info
            try {
                String macAddress = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + EC2MetadataKeys.mac), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
                AmazonNetworkInterface networkInterface = new AmazonNetworkInterface();
                networkInterface.setMac(macAddress);
                String vpcId = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + "/network/interfaces/macs/" + macAddress + "/vpc-id/"), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
                String subnetId = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + "/network/interfaces/macs/" + macAddress + "/subnet-id/"), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
                networkInterface.setNetwork(subnetId);

                ec2InstanceMetadata.publicIpV4 = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + "/network/interfaces/macs/" + macAddress + "/public-ipv4s/"), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
                ec2InstanceMetadata.privateIpV4 = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + "/network/interfaces/macs/" + macAddress + "/local-ipv4s/"), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
                networkInterface.setIpv4(ec2InstanceMetadata.privateIpV4);
                networkInterface.setId(readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + "/network/interfaces/macs/" + macAddress + "/interface-id/"), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS));
                networkInterface.setGateway(vpcId);
                ec2InstanceMetadata.interfaces = new ArrayList<>();
                ec2InstanceMetadata.interfaces.add(networkInterface);
            } catch (IOException e) {
                LOG.error("error getting public host name from:" + ec2InstanceMetadataURL + EC2MetadataKeys.publicHostname.getName(), e);
            }

            ec2InstanceMetadata.metadata = objectMapper.convertValue(ec2InstanceMetadata, Map.class);
            if (LOG.isDebugEnabled()) {
                LOG.debug("EC2 Metadata found:" + ec2InstanceMetadata.metadata.toString());
            }
            //TODO make individual calls for building network interfaces.. required recursive http calls for all mac addresses
        } catch (IOException e) {
            LOG.error("Error reading ec2 metadata url", e);
        }
        cachedMetadata = ec2InstanceMetadata;
        return Optional.of(ec2InstanceMetadata);
    }

    /**
     * Read EC2 metadata from the given URL.
     *
     * @param url URL to fetch AWS EC2 metadata information
     * @param connectionTimeoutMs connection timeout in millis
     * @param readTimeoutMs read timeout in millis
     * @return AWS EC2 metadata information
     * @throws IOException Signals that an I/O exception of some sort has occurred
     */
    protected JsonNode readEc2MetadataJson(URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {
        URLConnection urlConnection = url.openConnection();

        if (url.getProtocol().equalsIgnoreCase("file")) {
            urlConnection.connect();
            try (InputStream in = urlConnection.getInputStream()) {
                return objectMapper.readTree(in);
            }
        } else {
            HttpURLConnection uc = (HttpURLConnection) urlConnection;

            uc.setConnectTimeout(connectionTimeoutMs);
            uc.setReadTimeout(readTimeoutMs);
            uc.setRequestMethod("GET");
            uc.setDoOutput(true);
            int responseCode = uc.getResponseCode();
            try (InputStream in = uc.getInputStream()) {
                return objectMapper.readTree(in);
            }
        }
    }

    /**
     * Read EC2 metadata from the given URL.
     *
     * @param url URL to fetch AWS EC2 metadata information
     * @param connectionTimeoutMs connection timeout in millis
     * @param readTimeoutMs read timeout in millis
     * @return AWS EC2 metadata information
     * @throws IOException Signals that an I/O exception of some sort has occurred
     */
    protected String readEc2MetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {

        URLConnection urlConnection = url.openConnection();

        if (url.getProtocol().equalsIgnoreCase("file")) {
            if (url.getPath().indexOf(':') != -1) {
                //rebuild url path because windows can't have paths with colons
                url = new URL(url.getProtocol(), url.getHost(), url.getFile().replace(':', '_'));
                urlConnection = url.openConnection();
            }
            urlConnection.connect();
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(urlConnection.getInputStream()))) {
                return IOUtils.readText(in);
            }
        } else {
            HttpURLConnection uc = (HttpURLConnection) urlConnection;

            uc.setConnectTimeout(connectionTimeoutMs);
            uc.setReadTimeout(readTimeoutMs);
            uc.setRequestMethod("GET");
            uc.setDoOutput(true);
            int responseCode = uc.getResponseCode();
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(uc.getInputStream()))) {
                return IOUtils.readText(in);
            }
        }
    }
}
