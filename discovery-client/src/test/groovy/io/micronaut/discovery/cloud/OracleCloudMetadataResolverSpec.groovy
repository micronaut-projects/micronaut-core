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
import io.micronaut.discovery.cloud.oraclecloud.OracleCloudInstanceMetadata
import io.micronaut.discovery.cloud.oraclecloud.OracleCloudMetadataConfiguration
import io.micronaut.discovery.cloud.oraclecloud.OracleCloudMetadataResolver
import io.micronaut.discovery.cloud.oraclecloud.OracleCloudNetworkInterface
import spock.lang.Specification

import java.nio.file.Paths

class OracleCloudMetadataResolverSpec extends Specification {

    void "test building oracle cloud compute metadata"() {
        given:
        Environment environment = Mock(Environment)
        OracleCloudMetadataResolver resolver = buildResolver()
        Optional<OracleCloudInstanceMetadata> computeInstanceMetadata = resolver.resolve(environment) as Optional<OracleCloudInstanceMetadata>

        expect:
        computeInstanceMetadata.isPresent()
        computeInstanceMetadata.get().computePlatform == ComputePlatform.ORACLE_CLOUD
        computeInstanceMetadata.get().instanceId == "ocid1.instance.oc1.phx.abyhqljrg2v5zuydab6r5nbsywedkjvtwd57opwmuhfc5hg5jrxgs3jmg3ga"
        computeInstanceMetadata.get().name == "micronaut-env"
        computeInstanceMetadata.get().region == "us-phoenix-1"
    }


    private OracleCloudMetadataResolver buildResolver() {
        def configuration = new OracleCloudMetadataConfiguration()
        String currentPath = Paths.get("").toAbsolutePath().toString()
        configuration.url = "file:///${currentPath}/src/test/groovy/io/micronaut/discovery/cloud/oracleCloudInstanceMetadata.json"
        configuration.vnicUrl = "file:///${currentPath}/src/test/groovy/io/micronaut/discovery/cloud/oracleCloudInstanceNetworkMetadata.json"
        return new OracleCloudMetadataResolver(new ObjectMapper(), configuration)
    }
}
