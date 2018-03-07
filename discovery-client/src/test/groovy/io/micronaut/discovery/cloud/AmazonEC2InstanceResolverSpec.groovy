package io.micronaut.discovery.cloud

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.env.Environment
import io.micronaut.discovery.cloud.aws.AmazonComputeInstanceMetadataResolver
import io.micronaut.discovery.cloud.aws.AmazonMetadataConfiguration
import io.micronaut.context.env.ComputePlatform
import io.micronaut.context.env.Environment
import io.micronaut.discovery.cloud.aws.AmazonComputeInstanceMetadataResolver
import io.micronaut.discovery.cloud.aws.AmazonMetadataConfiguration
import spock.lang.Specification

import java.nio.file.Path
import java.nio.file.Paths

class AmazonEC2InstanceResolverSpec extends Specification {

    void "test building ec2 metadata"() {
        given:
        Environment environment = Mock(Environment)

        AmazonComputeInstanceMetadataResolver resolver = getResolver()

        Optional<ComputeInstanceMetadata> computeInstanceMetadata = resolver.resolve(environment)


        expect:
        computeInstanceMetadata.isPresent()
        computeInstanceMetadata.get().getInterfaces() != null
        NetworkInterface networkInterface = computeInstanceMetadata.get().getInterfaces().get(0)
        networkInterface.mac == "0a:5b:4b:a7:fb:5c"
        networkInterface.ipv4 == "172.30.3.54"
        networkInterface.network == "subnet-1e660468"
        networkInterface.gateway == "vpc-75d5d111"
        networkInterface.id == "eni-d88bca3d"


        computeInstanceMetadata.get().publicIpV4 == "34.230.77.169"
        computeInstanceMetadata.get().account == "057654311259"
        computeInstanceMetadata.get().availabilityZone == "us-east-1d"
        computeInstanceMetadata.get().computePlatform == ComputePlatform.AMAZON_EC2
        computeInstanceMetadata.get().instanceId == "i-0b629c0731440836a"
        computeInstanceMetadata.get().region == "us-east-1"
        computeInstanceMetadata.get().imageId == "ami-80861296"
        computeInstanceMetadata.get().privateIpV4 == "172.30.3.54"
        computeInstanceMetadata.get().localHostname == "localhost"

    }

    private AmazonComputeInstanceMetadataResolver getResolver() {
        AmazonMetadataConfiguration configuration = new AmazonMetadataConfiguration()
        Path currentRelativePath = Paths.get("");
        String s = currentRelativePath.toAbsolutePath().toString();
        System.out.println("Current relative path is: " + s);
        configuration.metadataUrl = "file:///${s}/src/test/groovy/io/micronaut/discovery/cloud/"
        configuration.instanceDocumentUrl = "file:///${s}/src/test/groovy/io/micronaut/discovery/cloud/identity-document.json"
        AmazonComputeInstanceMetadataResolver resolver = new AmazonComputeInstanceMetadataResolver(
                new ObjectMapper(),
                configuration
        )
        resolver
    }


    void "test caching ec2 metadata"() {
        given:
        Environment environment = Mock(Environment)
        AmazonComputeInstanceMetadataResolver resolver = getResolver()
        Optional<ComputeInstanceMetadata> computeInstanceMetadata = resolver.resolve(environment)


        expect:
        computeInstanceMetadata.isPresent()
        !computeInstanceMetadata.get().isCached()
        NetworkInterface networkInterface = computeInstanceMetadata.get().getInterfaces().get(0)

        Optional<ComputeInstanceMetadata> computeInstanceMetadata1 = resolver.resolve(environment)

        computeInstanceMetadata1.isPresent()
        computeInstanceMetadata1.get().isCached()

    }

}
