package io.micronaut.configuration.ribbon

import com.netflix.client.config.CommonClientConfigKey
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.reactivex.Flowable
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

class ManualServiceDiscoveryRibbonSpec extends Specification {

    void "test manual load balancer config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.http.services.foo.urls': 'http://google.com',
                'foo.ribbon.VipAddress':'test'
        )

        when:
        SomeService someService = ctx.getBean(SomeService)

        then:
        someService.client instanceof RibbonRxHttpClient
        someService.client.loadBalancer.isPresent()


        when:
        RibbonLoadBalancer balancer = (RibbonLoadBalancer) someService.client.loadBalancer.get()

        then:
        balancer.clientConfig
        balancer.clientConfig.get(CommonClientConfigKey.VipAddress) == 'test'

        when:
        def si = Flowable.fromPublisher(balancer.select()).blockingFirst()

        then:
        si.URI == URI.create("http://google.com:-1")

        cleanup:
        ctx.close()

    }

    void "test manual load balancer config with Ribbon config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'foo.ribbon.listOfServers':'http://google.com'
        )

        when:
        SomeService someService = ctx.getBean(SomeService)

        then:
        someService.client instanceof RibbonRxHttpClient
        someService.client.loadBalancer.isPresent()


        when:
        RibbonLoadBalancer balancer = (RibbonLoadBalancer) someService.client.loadBalancer.get()

        then:
        balancer.clientConfig

        when:
        def si = Flowable.fromPublisher(balancer.select()).blockingFirst()

        then:
        si.URI == URI.create("http://google.com:80")

        cleanup:
        ctx.close()

    }

    @Singleton
    static class SomeService {
        @Inject @Client('foo') RxHttpClient client

    }

}
