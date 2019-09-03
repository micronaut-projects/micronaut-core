package io.micronaut.docs.config.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.docs.config.env.DataSourceFactory.DataSource
import static org.junit.Assert.assertEquals

class EachBeanTest extends Specification {
    
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

    
    void "test each bean"() {
        // tag::beans[]
        when:
        Collection<DataSource> beansOfType = applicationContext.getBeansOfType(DataSource.class)
        assertEquals(2, beansOfType.size()) // <1>

        DataSource firstConfig = applicationContext.getBean(
                DataSource.class,
                Qualifiers.byName("one") // <2>
        )
        // end::beans[]
        then:
        firstConfig != null
    }
}
