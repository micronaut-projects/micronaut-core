/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.cloud.aws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.annotation.Value;
import org.particleframework.context.env.ComputePlatform;
import org.particleframework.context.env.Environment;
import org.particleframework.core.io.IOUtils;
import org.particleframework.discovery.cloud.ComputeInstanceMetadata;
import org.particleframework.discovery.cloud.ComputeInstanceMetadataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.net.www.protocol.file.FileURLConnection;

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
 * Resolves {@link ComputeInstanceMetadata} for Amazon EC2
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 *
 * @since 1.0
 */
@Singleton
@Requires(env=Environment.AMAZON_EC2)
public class AmazonComputeInstanceMetadataResolver implements ComputeInstanceMetadataResolver {
    private static final Logger LOG  = LoggerFactory.getLogger(AmazonComputeInstanceMetadataResolver.class);

    private final ObjectMapper objectMapper;
    private final AmazonMetadataConfiguration configuration;
    private AmazonEC2InstanceMetadata cachedMetadata;


    @Inject
    public AmazonComputeInstanceMetadataResolver(ObjectMapper objectMapper, AmazonMetadataConfiguration configuration) {
        this.objectMapper = objectMapper;
        this.configuration = configuration;
    }

    public AmazonComputeInstanceMetadataResolver() {
        this.objectMapper = new ObjectMapper();
        this.configuration = new AmazonMetadataConfiguration();
    }

    @Override
    public Optional<ComputeInstanceMetadata> resolve(Environment environment) {
        if(!configuration.isEnabled()) {
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
            JsonNode metadataJson = readEc2MetadataJson(new URL(ec2InstanceIdentityDocURL),5000,5000);
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
                ec2InstanceMetadata.localHostname = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+EC2MetadataKeys.localHostname.getName()),1000,5000);
            } catch (IOException e) {
                LOG.error("Error getting local hostname from url:"+ec2InstanceMetadataURL+EC2MetadataKeys.localHostname.name(),e);
            }
            try {
                ec2InstanceMetadata.publicHostname = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+EC2MetadataKeys.publicHostname.getName()),1000,5000);
            } catch (IOException e) {
                LOG.error("error getting public host name from:"+ec2InstanceMetadataURL+EC2MetadataKeys.publicHostname.name(),e);
            }
            // build up network info
            try {
                String macAddress = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+EC2MetadataKeys.mac),1000,5000);
                AmazonNetworkInterface networkInterface = new AmazonNetworkInterface();
                networkInterface.setMac(macAddress);
                String vpcId = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+"/network/interfaces/macs/"+macAddress+"/vpc-id/"),1000,5000);
                String subnetId = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+"/network/interfaces/macs/"+macAddress+"/subnet-id/"),1000,5000);
                networkInterface.setNetwork(subnetId);

                ec2InstanceMetadata.publicIpV4 = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+"/network/interfaces/macs/"+macAddress+"/public-ipv4s/"),1000,5000);
                ec2InstanceMetadata.privateIpV4 = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+"/network/interfaces/macs/"+macAddress+"/local-ipv4s/"),1000,5000);
                networkInterface.setIpv4(ec2InstanceMetadata.privateIpV4);
                networkInterface.setId(readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+"/network/interfaces/macs/"+macAddress+"/interface-id/"),1000,5000));
                networkInterface.setGateway(vpcId);
                ec2InstanceMetadata.interfaces = new ArrayList<>();
                ec2InstanceMetadata.interfaces.add(networkInterface);
            } catch (IOException e) {
                LOG.error("error getting public host name from:"+ec2InstanceMetadataURL+EC2MetadataKeys.publicHostname.getName(),e);
            }


            ec2InstanceMetadata.metadata = objectMapper.convertValue(ec2InstanceMetadata, Map.class);
            //TODO make individual calls for building network interfaces.. required recursive http calls for all mac addresses
        } catch (IOException e) {
            LOG.error("Error reading ec2 metadata url",e);
        }
        cachedMetadata = ec2InstanceMetadata;
        return Optional.of(ec2InstanceMetadata);
    }

    protected JsonNode readEc2MetadataJson(URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {
        URLConnection urlConnection = url.openConnection();

        if (urlConnection instanceof FileURLConnection) {

            FileURLConnection fu = (FileURLConnection)urlConnection;
            fu.connect();
            try(InputStream in = fu.getInputStream()) {
                return objectMapper.readTree(in);
            }

        } else {
            HttpURLConnection uc = (HttpURLConnection) urlConnection;

            uc.setConnectTimeout(connectionTimeoutMs);
            uc.setReadTimeout(readTimeoutMs);
            uc.setRequestMethod("GET");
            uc.setDoOutput(true);
            int responseCode = uc.getResponseCode();
            try(InputStream in = uc.getInputStream()) {
                return objectMapper.readTree(in);
            }
        }
    }

    protected String readEc2MetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {
        URLConnection urlConnection = url.openConnection();

        if (urlConnection instanceof FileURLConnection) {

            FileURLConnection fu = (FileURLConnection)urlConnection;
            fu.connect();
            try(BufferedReader in = new BufferedReader(
                    new InputStreamReader(fu.getInputStream()))) {
                return IOUtils.readText(in);
            }

        } else {
            HttpURLConnection uc = (HttpURLConnection) urlConnection;

            uc.setConnectTimeout(connectionTimeoutMs);
            uc.setReadTimeout(readTimeoutMs);
            uc.setRequestMethod("GET");
            uc.setDoOutput(true);
            int responseCode = uc.getResponseCode();
            try(BufferedReader in = new BufferedReader(
                    new InputStreamReader(uc.getInputStream()))) {
                return IOUtils.readText(in);
            }
        }
    }




}
