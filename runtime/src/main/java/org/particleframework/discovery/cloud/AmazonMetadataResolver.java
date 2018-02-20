package org.particleframework.discovery.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.annotation.Value;
import org.particleframework.context.env.ComputePlatform;
import org.particleframework.context.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.net.www.protocol.file.FileURLConnection;

import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * @author rvanderwerf
 * @since 1.0
 */
@Singleton
@Requires(env="ec2")
public class AmazonMetadataResolver implements MetadataResolver {
    private static final Logger LOG  = LoggerFactory.getLogger(AmazonMetadataResolver.class);
    private AmazonEC2InstanceMetadata cachedMetadata;

    @Value("particle.autodiscovery.ec2.instanceMetadata.doc.url:'http://169.254.169.254/latest/dynamic/instance-identity/document'")
    String ec2InstanceIdentityDocURL;

    @Value("particle.autodiscover.ec2.instanceMetadata.url:'http://169.254.169.254/latest/meta-data/'")
    String ec2InstanceMetadataURL;

    @Override
    public Optional<? extends ComputeInstanceMetadata> resolve(Environment environment) {
        if (cachedMetadata != null) {
            cachedMetadata.cached = true;
            return Optional.of(cachedMetadata);
        }
        AmazonEC2InstanceMetadata ec2InstanceMetadata = new AmazonEC2InstanceMetadata();
        String result = "";
        try {
            result = readEc2MetadataUrl(new URL(ec2InstanceIdentityDocURL),5000,5000);
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
                ec2InstanceMetadata.localHostname = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+EC2MetadataKeys.localHostname.name),1000,5000);
            } catch (IOException e) {
                LOG.error("Error getting local hostname from url:"+ec2InstanceMetadataURL+EC2MetadataKeys.localHostname.name,e);
            }
            try {
                ec2InstanceMetadata.publicHostname = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+EC2MetadataKeys.publicHostname.name),1000,5000);
            } catch (IOException e) {
                LOG.error("error getting public host name from:"+ec2InstanceMetadataURL+EC2MetadataKeys.publicHostname.name,e);
            }
            // build up network info
            try {
                String macAddress = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+EC2MetadataKeys.mac),1000,5000);
                NetworkInterface networkInterface = new NetworkInterface();
                networkInterface.mac = macAddress;
                String vpcId = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+"/network/interfaces/macs/"+macAddress+"/vpc-id/"),1000,5000);
                String subnetId = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+"/network/interfaces/macs/"+macAddress+"/subnet-id/"),1000,5000);
                networkInterface.network = subnetId;

                ec2InstanceMetadata.publicIpV4 = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+"/network/interfaces/macs/"+macAddress+"/public-ipv4s/"),1000,5000);
                ec2InstanceMetadata.privateIpV4 = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+"/network/interfaces/macs/"+macAddress+"/local-ipv4s/"),1000,5000);
                networkInterface.ipv4 = ec2InstanceMetadata.privateIpV4;
                networkInterface.id = readEc2MetadataUrl(new URL(ec2InstanceMetadataURL+"/network/interfaces/macs/"+macAddress+"/interface-id/"),1000,5000);
                networkInterface.gateway = vpcId;
                ec2InstanceMetadata.interfaces = new ArrayList<NetworkInterface>();
                ec2InstanceMetadata.interfaces.add(networkInterface);
            } catch (IOException e) {
                LOG.error("error getting public host name from:"+ec2InstanceMetadataURL+EC2MetadataKeys.publicHostname.name,e);
            }


            ec2InstanceMetadata.metadata = mapper.convertValue(ec2InstanceMetadata, Map.class);
            //TODO make individual calls for building network interfaces.. required recursive http calls for all mac addresses
        } catch (IOException e) {
            LOG.error("Error reading ec2 metadata url",e);
        }
        cachedMetadata = ec2InstanceMetadata;
        return Optional.of(ec2InstanceMetadata);
    }

    String readEc2MetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {

        URLConnection urlConnection = url.openConnection();

        StringBuffer response = new StringBuffer();

        if (urlConnection instanceof FileURLConnection) {

            FileURLConnection fu = (FileURLConnection)urlConnection;
            fu.connect();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(fu.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

        } else {
            HttpURLConnection uc = (HttpURLConnection) urlConnection;

            uc.setConnectTimeout(connectionTimeoutMs);
            uc.setReadTimeout(readTimeoutMs);
            uc.setRequestMethod("GET");
            uc.setDoOutput(true);
            int responseCode = uc.getResponseCode();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(uc.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
        }
        return response.toString();
    }




}
