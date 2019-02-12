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
package io.micronaut.discovery.eureka

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.eureka.client.v2.AmazonInfo
import io.micronaut.discovery.eureka.client.v2.InstanceInfo
import spock.lang.Specification

class EurekaInstanceInfoSpec extends Specification {

    void "test deserialize amazon instance info"() {
        given:
        InstanceInfo ii = new InstanceInfo("localhost", "foo")
        def info = new AmazonInfo()
        info.setMetadata(
                (AmazonInfo.MetaDataKey.accountId.toString()): '1234',
                (AmazonInfo.MetaDataKey.availabilityZone.toString()): 'eu1'
        )
        ii.setDataCenterInfo(info)
        def ctx = ApplicationContext.run()
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)
        def str = objectMapper.writeValueAsString(ii)
        ii = objectMapper.readValue(str, InstanceInfo)

        expect:
        ii.app == 'foo'
        ii.dataCenterInfo instanceof AmazonInfo
        ii.dataCenterInfo.metadata.size() == 2

        cleanup:
        ctx.close()
    }
}
