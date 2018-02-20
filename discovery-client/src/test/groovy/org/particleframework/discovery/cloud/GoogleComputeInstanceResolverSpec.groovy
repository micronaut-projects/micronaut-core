package org.particleframework.discovery.cloud

import org.particleframework.context.env.ComputePlatform
import org.particleframework.context.env.Environment
import spock.lang.Specification

import java.nio.file.Path
import java.nio.file.Paths

class GoogleComputeInstanceResolverSpec extends Specification {


    void "test building google compute metadata"() {
        given:
        Environment environment = Mock(Environment)

        GoogleComputeMetadataResolver resolver = new GoogleComputeMetadataResolver()
        Path currentRelativePath = Paths.get("");
        String s = currentRelativePath.toAbsolutePath().toString();
        System.out.println("Current relative path is: " + s);

        resolver.gcMetadataURL = "file:///${s}/src/test/groovy/org/particleframework/discovery/cloud/gcInstanceMetadata.json"
        resolver.gcProjectMetadataURL = "file:///${s}/src/test/groovy/org/particleframework/discovery/cloud/projectMetadata.json"
        Optional<ComputeInstanceMetadata> computeInstanceMetadata = resolver.resolve(environment)


        expect:
        computeInstanceMetadata.isPresent()
        computeInstanceMetadata.get().getInterfaces() != null
        computeInstanceMetadata.get().getInterfaces().get(0).ipv4 == "10.142.0.2"
        computeInstanceMetadata.get().getInterfaces().get(0).network == "projects/406777119879/networks/default"
        computeInstanceMetadata.get().getInterfaces().get(0).gateway == "10.142.0.1"
        computeInstanceMetadata.get().getInterfaces().get(0).netmask == "255.255.240.0"
        computeInstanceMetadata.get().getInterfaces().get(0).mac == "42:01:0a:8e:00:02"
        computeInstanceMetadata.get().account == "shaped-icon-194504"
        computeInstanceMetadata.get().availabilityZone == "projects/406777119879/zones/us-east1-b"
        computeInstanceMetadata.get().computePlatform == ComputePlatform.GOOGLE_COMPUTE
        computeInstanceMetadata.get().instanceId == "9086091857655938011"
        computeInstanceMetadata.get().getRegion() == "projects/406777119879/zones/us-east1"
        computeInstanceMetadata.get().imageId == "projects/debian-cloud/global/images/debian-9-stretch-v20180206"
        computeInstanceMetadata.get().privateIpV4 == "10.142.0.2"
        computeInstanceMetadata.get().publicIpV4 == "35.190.174.64"
    }


}
