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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
@Requires(env="gc")
public class GoogleComputeMetadataResolver implements MetadataResolver {

    @Value("org.particleframework.cloud.googleComputeMetadataURL:'http://metadata.google.internal/computeMetadata/v1/project/'")
    String gcMetadataURL;

    @Value("org.particleframework.cloud.googleComputeMetadataURL:'http://metadata.google.internal/project/v1/project/'")
    String gcProjectMetadataURL;

    private static final Logger LOG  = LoggerFactory.getLogger(GoogleComputeMetadataResolver.class);

    @Override
    public Optional<? extends ComputeInstanceMetadata> resolve(Environment environment) {
        // not implemented yet
        try {
            String projectResult = readGcMetadataUrl(new URL(gcProjectMetadataURL+"?recursive=true"),5000,5000);
            String instanceResult = readGcMetadataUrl(new URL(gcMetadataURL+"?recursive=true"),5000,5000);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode instanceMetadataJson = mapper.readTree(instanceResult);
            JsonNode projectResultJson = mapper.readTree(projectResult);


            if (instanceMetadataJson!=null) {

                GoogleComputeInstanceMetadata instanceMetadata = new GoogleComputeInstanceMetadata();
                instanceMetadata.instanceId = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.ID.name).asText();
                instanceMetadata.account = projectResultJson.findValue(GoogleComputeMetadataKeys.PROJECT_ID.name).textValue();
                instanceMetadata.availabilityZone = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.ZONE.name).textValue();
                instanceMetadata.machineType = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.MACHINE_TYPE.name).textValue();
                instanceMetadata.computePlatform = ComputePlatform.GOOGLE_COMPUTE;
                instanceMetadata.description = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.DESCRIPTION.name).textValue();
                instanceMetadata.imageId = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.IMAGE.name).textValue();
                instanceMetadata.localHostname = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.HOSTNAME.name).textValue();
                instanceMetadata.name = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.NAME.name).textValue();
                JsonNode networkInterfaces = instanceMetadataJson.findValue(GoogleComputeMetadataKeys.NETWORK_INTERFACES.name);
                List<NetworkInterface> interfaces = new ArrayList<NetworkInterface>();
                AtomicReference<Integer> networkCounter = new AtomicReference<>(0);
                networkInterfaces.elements().forEachRemaining(
                 jsonNode -> {
                     NetworkInterface networkInterface = new NetworkInterface();
                     networkInterface.id = networkCounter.toString();
                     if (jsonNode.findValue(GoogleComputeMetadataKeys.ACCESS_CONFIGS.name)!=null) {
                         JsonNode accessConfigs = jsonNode.findValue(GoogleComputeMetadataKeys.ACCESS_CONFIGS.name);
                         // we just grab the first one
                         instanceMetadata.publicIpV4 = accessConfigs.get(0).findValue("externalIp").textValue();
                     }
                     if (jsonNode.findValue(GoogleComputeMetadataKeys.IP.name)!=null) {
                         networkInterface.ipv4 = jsonNode.findValue(GoogleComputeMetadataKeys.IP.name).textValue();
                         instanceMetadata.privateIpV4 = jsonNode.findValue(GoogleComputeMetadataKeys.IP.name).textValue();
                     }
                     if (jsonNode.findValue(GoogleComputeMetadataKeys.MAC.name)!=null) {
                         networkInterface.mac = jsonNode.findValue(GoogleComputeMetadataKeys.MAC.name).textValue();
                     }
                     if (jsonNode.findValue(GoogleComputeMetadataKeys.NETWORK.name)!=null) {
                         networkInterface.network = jsonNode.findValue(GoogleComputeMetadataKeys.NETWORK.name).textValue();
                     }
                     if (jsonNode.findValue(GoogleComputeMetadataKeys.NETMASK.name)!=null) {
                         networkInterface.netmask = jsonNode.findValue(GoogleComputeMetadataKeys.NETMASK.name).textValue();
                     }
                     if (jsonNode.findValue(GoogleComputeMetadataKeys.GATEWAY.name)!=null) {
                         networkInterface.gateway = jsonNode.findValue(GoogleComputeMetadataKeys.GATEWAY.name).textValue();
                     }
                     networkCounter.getAndSet(networkCounter.get() + 1);
                     interfaces.add(networkInterface);

                 });
                instanceMetadata.interfaces = interfaces;
                instanceMetadata.metadata = mapper.convertValue(instanceMetadata, Map.class);

                return Optional.of(instanceMetadata);

            }
        } catch (MalformedURLException me) {
            LOG.error("org.particleframework.cloud.googleComputeMetadataURL config value"+gcMetadataURL+"?recursive=true is invalid!",me);

        } catch (IOException ioe) {
            LOG.error("Error connecting to"+gcMetadataURL+"?recursive=true reading instance metadata",ioe);
        }



        return Optional.empty();
    }


    String readGcMetadataUrl(URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {

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
            uc.setRequestProperty("Metadata-Flavor","Google");
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
