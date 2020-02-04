package io.micronaut.docs.config.env

import io.kotlintest.specs.AnnotationSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.core.util.CollectionUtils
import io.micronaut.inject.qualifiers.Qualifiers
import org.junit.Test

import java.net.URI
import java.net.URISyntaxException

import org.junit.Assert.assertEquals

class EachPropertyTest : AnnotationSpec() {

    @Test
    @Throws(URISyntaxException::class)
    fun testEachProperty() {
        // tag::config[]
        val applicationContext = ApplicationContext.run(PropertySource.of(
                "test",
                mapOf(
                        "test.datasource.one.url" to "jdbc:mysql://localhost/one",
                        "test.datasource.two.url" to "jdbc:mysql://localhost/two"
                )
        ))
        // end::config[]

        // tag::beans[]
        val beansOfType = applicationContext.getBeansOfType(DataSourceConfiguration::class.java)
        assertEquals(2, beansOfType.size.toLong()) // <1>

        val firstConfig = applicationContext.getBean(
                DataSourceConfiguration::class.java,
                Qualifiers.byName("one") // <2>
        )

        assertEquals(
                URI("jdbc:mysql://localhost/one"),
                firstConfig.url
        )
        // end::beans[]
        applicationContext.close()
    }
}
