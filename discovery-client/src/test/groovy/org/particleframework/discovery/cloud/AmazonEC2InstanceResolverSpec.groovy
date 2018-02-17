package org.particleframework.discovery.cloud

import org.particleframework.context.env.ComputePlatform
import org.particleframework.context.env.Environment
import org.particleframework.context.env.PropertySource
import org.particleframework.context.env.PropertySourcePropertyResolver
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Path
import java.nio.file.Paths

class AmazonEC2InstanceResolverSpec extends Specification {


    void "test building ec2 metadata"() {
        given:
        Environment environment = Mock(Environment)

        AmazonMetadataResolver resolver = new AmazonMetadataResolver()
        Path currentRelativePath = Paths.get("");
        String s = currentRelativePath.toAbsolutePath().toString();
        System.out.println("Current relative path is: " + s);

        resolver.ec2InstanceMetadataURL = "file:///${s}/src/test/groovy/org/particleframework/discovery/cloud/"
        resolver.ec2InstanceIdentityDocURL = "file:///${s}/src/test/groovy/org/particleframework/discovery/cloud/identity-document.json"
        Optional<ComputeInstanceMetadata> computeInstanceMetadata = resolver.resolve(environment)


        expect:
        computeInstanceMetadata.isPresent()
        computeInstanceMetadata.get().account == "057654311259"
        computeInstanceMetadata.get().availabilityZone == "us-east-1d"
        computeInstanceMetadata.get().computePlatform == ComputePlatform.AMAZON_EC2
        computeInstanceMetadata.get().instanceId == "i-0b629c0731440836a"
        computeInstanceMetadata.get().region == "us-east-1"
        computeInstanceMetadata.get().imageId == "ami-80861296"
        computeInstanceMetadata.get().privateIpV4 == "172.30.3.54"
        computeInstanceMetadata.get().localHostname == "localhost"
        computeInstanceMetadata.get().h == "172.30.3.54"
    }


}
