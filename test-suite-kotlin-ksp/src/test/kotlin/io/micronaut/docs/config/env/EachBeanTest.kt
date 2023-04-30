package io.micronaut.docs.config.env

import io.kotest.core.spec.style.AnnotationSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.docs.config.env.DataSourceFactory.DataSource
import io.micronaut.inject.qualifiers.Qualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.net.URISyntaxException

class EachBeanTest : AnnotationSpec() {

    @Test
    @Throws(URISyntaxException::class)
    fun testEachBean() {
        // tag::config[]
        val applicationContext = ApplicationContext.run(PropertySource.of(
                "test",
                mapOf(
                        "test.datasource.one.url" to "jdbc:mysql://localhost/one",
                        "test.datasource.two.url" to "jdbc:mysql://localhost/two")
        ))
        // end::config[]

        // tag::beans[]
        val beansOfType = applicationContext.getBeansOfType(DataSource::class.java)
        assertEquals(2, beansOfType.size) // <1>

        val firstConfig = applicationContext.getBean(
                DataSource::class.java,
                Qualifiers.byName("one") // <2>
        )
        // end::beans[]

        assertNotNull(firstConfig)

        applicationContext.close()
    }
}
