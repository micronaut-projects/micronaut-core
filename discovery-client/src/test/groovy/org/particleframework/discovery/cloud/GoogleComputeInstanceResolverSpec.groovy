package org.particleframework.discovery.cloud

import com.fasterxml.jackson.databind.ObjectMapper
import org.particleframework.context.env.ComputePlatform
import org.particleframework.context.env.Environment
import org.particleframework.discovery.cloud.gcp.GoogleComputeInstanceMetadataResolver
import org.particleframework.discovery.cloud.gcp.GoogleComputeMetadataConfiguration
import spock.lang.Specification

import java.nio.file.Path
import java.nio.file.Paths

/**
 * @author rvanderwerf
 * @since 1.0
 */

class GoogleComputeInstanceResolverSpec extends Specification {


    void "test building google compute metadata"() {
        given:
        Environment environment = Mock(Environment)

        GoogleComputeInstanceMetadataResolver resolver = buildResolver()
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

    private GoogleComputeInstanceMetadataResolver buildResolver() {
        def configuration = new GoogleComputeMetadataConfiguration()
        Path currentRelativePath = Paths.get("");
        String s = currentRelativePath.toAbsolutePath().toString();
        System.out.println("Current relative path is: " + s);

        configuration.metadataUrl = "file:///${s}/src/test/groovy/org/particleframework/discovery/cloud/gcInstanceMetadata.json"
        configuration.projectMetadataUrl = "file:///${s}/src/test/groovy/org/particleframework/discovery/cloud/projectMetadata.json"
        GoogleComputeInstanceMetadataResolver resolver = new GoogleComputeInstanceMetadataResolver(
                new ObjectMapper(),
                configuration

        )
        resolver
    }


    void "test metadata caching"() {
        given:
        Environment environment = Mock(Environment)

        GoogleComputeInstanceMetadataResolver resolver = buildResolver()
        Optional<ComputeInstanceMetadata> computeInstanceMetadata = resolver.resolve(environment)


        expect:
        computeInstanceMetadata.isPresent()
        !computeInstanceMetadata.get().isCached()

        Optional<ComputeInstanceMetadata> computeInstanceMetadata1 = resolver.resolve(environment)
        computeInstanceMetadata1.isPresent()
        computeInstanceMetadata1.get().isCached()

    }


}
