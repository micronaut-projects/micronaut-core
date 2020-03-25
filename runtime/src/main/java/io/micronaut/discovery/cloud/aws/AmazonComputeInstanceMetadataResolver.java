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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Pattern;

import static io.micronaut.discovery.cloud.ComputeInstanceMetadataResolverUtils.*;
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

    private static final Pattern DRIVE_LETTER_PATTERN = Pattern.compile("^\\/*[a-zA-z]:.*$");

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
            cachedMetadata.setCached(true);
            return Optional.of(cachedMetadata);
        }
        AmazonEC2InstanceMetadata ec2InstanceMetadata = new AmazonEC2InstanceMetadata();
        try {
            String ec2InstanceIdentityDocURL = configuration.getInstanceDocumentUrl();
            String ec2InstanceMetadataURL = configuration.getMetadataUrl();
            JsonNode metadataJson = readMetadataUrl(new URL(ec2InstanceIdentityDocURL), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS, objectMapper, new HashMap<>());
            if (metadataJson != null) {
                stringValue(metadataJson, EC2MetadataKeys.instanceId.name()).ifPresent(ec2InstanceMetadata::setInstanceId);
                stringValue(metadataJson, EC2MetadataKeys.accountId.name()).ifPresent(ec2InstanceMetadata::setAccount);
                stringValue(metadataJson, EC2MetadataKeys.availabilityZone.name()).ifPresent(ec2InstanceMetadata::setAvailabilityZone);
                stringValue(metadataJson, EC2MetadataKeys.instanceType.name()).ifPresent(ec2InstanceMetadata::setMachineType);
                stringValue(metadataJson, EC2MetadataKeys.region.name()).ifPresent(ec2InstanceMetadata::setRegion);
                stringValue(metadataJson, "privateIp").ifPresent(ec2InstanceMetadata::setPrivateIpV4);
                stringValue(metadataJson, "imageId").ifPresent(ec2InstanceMetadata::setImageId);
            }
            try {
                ec2InstanceMetadata.setLocalHostname(readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + EC2MetadataKeys.localHostname.getName()), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS));
            } catch (IOException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error getting local hostname from url:" + ec2InstanceMetadataURL + EC2MetadataKeys.localHostname.name(), e);
                }
            }
            try {
                ec2InstanceMetadata.setPublicHostname(readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + EC2MetadataKeys.publicHostname.getName()), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS));
            } catch (IOException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("error getting public host name from:" + ec2InstanceMetadataURL + EC2MetadataKeys.publicHostname.name(), e);
                }
            }
            // build up network info
            try {
                String macAddress = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + EC2MetadataKeys.mac), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
                AmazonNetworkInterface networkInterface = new AmazonNetworkInterface();
                networkInterface.setMac(macAddress);
                String vpcId = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + "/network/interfaces/macs/" + macAddress + "/vpc-id/"), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
                String subnetId = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + "/network/interfaces/macs/" + macAddress + "/subnet-id/"), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS);
                networkInterface.setNetwork(subnetId);

                ec2InstanceMetadata.setPublicIpV4(readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + "/network/interfaces/macs/" + macAddress + "/public-ipv4s/"), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS));
                ec2InstanceMetadata.setPrivateIpV4(readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + "/network/interfaces/macs/" + macAddress + "/local-ipv4s/"), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS));
                networkInterface.setIpv4(ec2InstanceMetadata.getPrivateIpV4());
                networkInterface.setId(readEc2MetadataUrl(new URL(ec2InstanceMetadataURL + "/network/interfaces/macs/" + macAddress + "/interface-id/"), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS));
                networkInterface.setGateway(vpcId);
                ec2InstanceMetadata.setInterfaces(new ArrayList<>());
                ec2InstanceMetadata.getInterfaces().add(networkInterface);
            } catch (IOException e) {
                LOG.error("error getting public host name from:" + ec2InstanceMetadataURL + EC2MetadataKeys.publicHostname.getName(), e);
            }

            Map<?, ?> metadata = objectMapper.convertValue(ec2InstanceMetadata, Map.class);
            populateMetadata(ec2InstanceMetadata, metadata);
            if (LOG.isDebugEnabled()) {
                LOG.debug("EC2 Metadata found:" + ec2InstanceMetadata.getMetadata().toString());
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
    private String readEc2MetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {

        if (url.getProtocol().equalsIgnoreCase("file")) {
            url = rewriteUrl(url);
            URLConnection urlConnection = url.openConnection();
            urlConnection.connect();
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(urlConnection.getInputStream()))) {
                return IOUtils.readText(in);
            }
        } else {
            URLConnection urlConnection = url.openConnection();
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

    private URL rewriteUrl(URL url) throws MalformedURLException {
        String path = url.getPath();
        if (path.indexOf(':') != -1) {
            boolean driveLetterFound = DRIVE_LETTER_PATTERN.matcher(path).matches();
            path = path.replace(':', '_');
            if (driveLetterFound) {
                path = path.replaceFirst("_", ":");
            }
            url = new URL(url.getProtocol(), url.getHost(), path);
        }
        return url;
    }
}
