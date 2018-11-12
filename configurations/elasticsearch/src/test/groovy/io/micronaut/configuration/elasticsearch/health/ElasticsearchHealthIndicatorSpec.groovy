package io.micronaut.configuration.elasticsearch.health

import groovy.json.JsonSlurper
import io.micronaut.configuration.elasticsearch.ElasticsearchConfigurationProperties
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import io.reactivex.Flowable
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.shaded.org.apache.http.auth.AuthScope
import org.testcontainers.shaded.org.apache.http.auth.UsernamePasswordCredentials
import org.testcontainers.shaded.org.apache.http.client.CredentialsProvider
import org.testcontainers.shaded.org.apache.http.impl.client.BasicCredentialsProvider
import spock.lang.Specification

/**
 * @author puneetbehl
 * @since 1.1.0
 */
class ElasticsearchHealthIndicatorSpec extends Specification {

    void "test elasticsearch health indicator"() {
        given:
        ElasticsearchContainer container = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:6.4.1")
        container.start()

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider()
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("elastic", "changeme"))

        ApplicationContext applicationContext = ApplicationContext.run('elasticsearch.httpHosts': "http://${container.getHttpHostAddress()}")

        expect:
        applicationContext.containsBean(ElasticsearchConfigurationProperties)

        when:
        ElasticsearchHealthIndicator indicator = applicationContext.getBean(ElasticsearchHealthIndicator)
        HealthResult result = Flowable.fromPublisher(indicator.getResult()).blockingFirst()

        then:
        result.status == HealthStatus.UP
        new JsonSlurper().parseText((String) result.details).status == "green"

        when:
        container.stop()
        result = Flowable.fromPublisher(indicator.getResult()).blockingFirst()

        then:
        result.status == HealthStatus.DOWN


        cleanup:
        applicationContext?.stop()
    }

    void "test that ElasticsearchHealthIndicator is not created when the endpoints.health.elasticsearch.rest.high.level.enabled is set to false "() {
        ApplicationContext applicationContext = ApplicationContext.run(
                'elasticsearch.httpHosts': "http://localhost:9200",
                'endpoints.health.elasticsearch.rest.high.level.enabled': "false"

        )

        when:
        applicationContext.getBean(ElasticsearchHealthIndicator)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        applicationContext?.stop()
    }

}
