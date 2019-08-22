package io.micronaut.docs.config.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.core.util.CollectionUtils
import io.micronaut.inject.qualifiers.Qualifiers
import org.junit.Test
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.junit.Assert.assertEquals

class EachPropertyTest extends Specification{

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
        assertEquals(2, beansOfType.size()) // <1>

        DataSourceConfiguration firstConfig = applicationContext.getBean(
                DataSourceConfiguration.class,
                Qualifiers.byName("one") // <2>
        )

        then:
        new URI("jdbc:mysql://localhost/one") == firstConfig.getUrl()
        // end::beans[]
    }
}
