package io.micronaut.docs.config.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EachPropertyTest extends Specification {

    @AutoCleanup
    @Shared
    // tag::config[]
    ApplicationContext applicationContext = ApplicationContext.run(PropertySource.of(
            "test",
            [
                    "test.datasource.one.url": "jdbc:mysql://localhost/one",
                    "test.datasource.two.url": "jdbc:mysql://localhost/two"
            ]
    ))
    // end::config[]

    void "test each property"() {
        // tag::beans[]
        when:
        Collection<DataSourceConfiguration> beansOfType = applicationContext.getBeansOfType(DataSourceConfiguration.class)

        then:
        beansOfType.size() == 2 // <1>

        when:
        DataSourceConfiguration firstConfig = applicationContext.getBean(
                DataSourceConfiguration.class,
                Qualifiers.byName("one") // <2>
        )

        then:
        new URI("jdbc:mysql://localhost/one") == firstConfig.getUrl()
        // end::beans[]
    }

    void "test each property list"() {
        ApplicationContext applicationContext = ApplicationContext.run(
                ["ratelimits": [
                        [period: "10s", limit: "1000"],
                        [period: "1m", limit: "5000"]]])

        List<RateLimitsConfiguration> beansOfType = applicationContext.streamOfType(RateLimitsConfiguration.class).toList()

        expect:
        beansOfType.size() == 2
        beansOfType[0].limit == 1000
        beansOfType[1].limit == 5000

        applicationContext.close()
    }
}
