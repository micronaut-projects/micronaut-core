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
