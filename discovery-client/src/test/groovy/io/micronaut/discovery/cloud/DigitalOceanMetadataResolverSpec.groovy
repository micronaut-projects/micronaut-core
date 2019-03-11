/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.discovery.cloud

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.env.ComputePlatform
import io.micronaut.context.env.Environment
import io.micronaut.discovery.cloud.digitalocean.DigitalOceanInstanceMetadata
import io.micronaut.discovery.cloud.digitalocean.DigitalOceanMetadataConfiguration
import io.micronaut.discovery.cloud.digitalocean.DigitalOceanMetadataResolver
import io.micronaut.discovery.cloud.digitalocean.DigitalOceanNetworkInterface
import spock.lang.Specification

import java.nio.file.Paths

class DigitalOceanMetadataResolverSpec extends Specification {

    void "test building digital ocean compute metadata"() {
        given:
        Environment environment = Mock(Environment)
        DigitalOceanMetadataResolver resolver = buildResolver()
        Optional<DigitalOceanInstanceMetadata> computeInstanceMetadata = resolver.resolve(environment) as Optional<DigitalOceanInstanceMetadata>

        expect:
        computeInstanceMetadata.isPresent()
        computeInstanceMetadata.get().getInstanceId() == "2756294"
        computeInstanceMetadata.get().computePlatform == ComputePlatform.DIGITAL_OCEAN
        computeInstanceMetadata.get().instanceId == "2756294"
        computeInstanceMetadata.get().name == "sample-droplet"
        computeInstanceMetadata.get().vendorData == "#cloud-config\ndisable_root: false\nmanage_etc_hosts: true\n\ncloud_config_modules:\n - ssh\n - set_hostname\n - [ update_etc_hosts, once-per-instance ]\n\ncloud_final_modules:\n - scripts-vendor\n - scripts-per-once\n - scripts-per-boot\n - scripts-per-instance\n - scripts-user\n"
        computeInstanceMetadata.get().region == "nyc3"
        computeInstanceMetadata.get().interfaces.size() == 2
        computeInstanceMetadata.get().interfaces.find { it.ipv4 == "10.132.255.113"}.netmask == "255.255.0.0"
        computeInstanceMetadata.get().interfaces.find { it.ipv4 == "10.132.255.113"}.gateway == "10.132.0.1"
        computeInstanceMetadata.get().interfaces.find { it.ipv4 == "10.132.255.113"}.mac == "04:01:2a:0f:2a:02"

        computeInstanceMetadata.get().interfaces.find { it.ipv4 == "104.131.20.105"}.netmask == "255.255.192.0"
        computeInstanceMetadata.get().interfaces.find { it.ipv4 == "104.131.20.105"}.gateway == "104.131.0.1"

        ((DigitalOceanNetworkInterface)computeInstanceMetadata.get().interfaces.find { it.ipv6 == "2604:A880:0800:0010:0000:0000:017D:2001"}).cidr == 64
        ((DigitalOceanNetworkInterface)computeInstanceMetadata.get().interfaces.find { it.ipv6 == "2604:A880:0800:0010:0000:0000:017D:2001"}).ipv6Gateway == "2604:A880:0800:0010:0000:0000:0000:0001"

    }


    private DigitalOceanMetadataResolver buildResolver() {
        def configuration = new DigitalOceanMetadataConfiguration()
        String currentPath = Paths.get("").toAbsolutePath().toString()
        configuration.url = "file:///${currentPath}/src/test/groovy/io/micronaut/discovery/cloud/digitalOceanInstanceMetadata.json"

        return new DigitalOceanMetadataResolver(new ObjectMapper(), configuration)
    }
}
