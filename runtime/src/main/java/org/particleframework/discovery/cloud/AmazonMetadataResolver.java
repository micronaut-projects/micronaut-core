package org.particleframework.discovery.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.env.ComputePlatform;
import org.particleframework.context.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Optional;

@Singleton
@Requires(env="ec2")
public class AmazonMetadataResolver implements MetadataResolver {
    private static final Logger LOG  = LoggerFactory.getLogger(AmazonMetadataResolver.class);

    @Override
    public Optional<? extends ComputeInstanceMetadata> resolve(Environment environment) {
        AmazonEC2InstanceMetadata ec2InstanceMetadata = new AmazonEC2InstanceMetadata();
        String result = "";
        try {
            result = readEc2MetadataUrl(new URL("http://localhost/latest/dynamic/instance-identity/document"),5000,5000);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode metadataJson = mapper.readTree(result);
            if (metadataJson != null) {
                ec2InstanceMetadata.account = metadataJson.findValue(EC2MetadataKeys.accountId.name).textValue();
                ec2InstanceMetadata.availabilityZone = metadataJson.findValue("availabilityZone").textValue();
                ec2InstanceMetadata.instanceId = metadataJson.findValue("instanceId").textValue();
                ec2InstanceMetadata.machineType = metadataJson.findValue("instanceType").textValue();
                ec2InstanceMetadata.privateIpV4 = metadataJson.findValue("privateIp").textValue();
                ec2InstanceMetadata.region = metadataJson.findValue("region").textValue();
                ec2InstanceMetadata.imageId = metadataJson.findValue("imageId").textValue();
                ec2InstanceMetadata.computePlatform = ComputePlatform.AMAZON_EC2;
            }
            try {
                ec2InstanceMetadata.localHostname = readEc2MetadataUrl(new URL(EC2MetadataKeys.AWS_METADATA_URL+EC2MetadataKeys.localHostname.name),1000,5000);
            } catch (IOException e) {
                LOG.error("Error getting local hostname from url:"+EC2MetadataKeys.AWS_METADATA_URL+EC2MetadataKeys.localHostname.name,e);
            }
            try {
                ec2InstanceMetadata.publicHostname = readEc2MetadataUrl(new URL(EC2MetadataKeys.AWS_METADATA_URL+EC2MetadataKeys.publicHostname.name),1000,5000);
            } catch (IOException e) {
                LOG.error("error getting public host name from:"+EC2MetadataKeys.AWS_METADATA_URL+EC2MetadataKeys.publicHostname.name,e);
            }
            ec2InstanceMetadata.metadata = mapper.convertValue(ec2InstanceMetadata, Map.class);
            //TODO make individual calls for building network interfaces.. required recursive http calls for all mac addresses
        } catch (IOException e) {
            LOG.error("Error reading ec2 metadata url",e);
        }
        return Optional.of(ec2InstanceMetadata);
    }

    String readEc2MetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {
        HttpURLConnection uc = (HttpURLConnection) url.openConnection();
        uc.setConnectTimeout(connectionTimeoutMs);
        uc.setReadTimeout(readTimeoutMs);
        uc.setRequestMethod("GET");
        uc.setDoOutput(true);
        int responseCode = uc.getResponseCode();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(uc.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response.toString();
    }




}
