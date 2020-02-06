package io.micronaut.docs.config.env

import io.kotlintest.specs.AnnotationSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.core.util.CollectionUtils
import io.micronaut.inject.qualifiers.Qualifiers
import org.junit.Assert.assertEquals
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.stream.Collectors

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

    @Test
    fun testEachPropertyList() {
        val limits: MutableList<Map<*, *>> = ArrayList()
        limits.add(CollectionUtils.mapOf("period", "10s", "limit", "1000"))
        limits.add(CollectionUtils.mapOf("period", "1m", "limit", "5000"))
        val applicationContext = ApplicationContext.run(mapOf("ratelimits" to listOf(mapOf("period" to "10s", "limit" to "1000"), mapOf("period" to "1m", "limit" to "5000"))))

        val beansOfType = applicationContext.streamOfType(RateLimitsConfiguration::class.java).collect(Collectors.toList())

        assertEquals(
                2,
                beansOfType.size
                        .toLong())
        assertEquals(1000L, beansOfType[0].limit?.toLong())
        assertEquals(5000L, beansOfType[1].limit?.toLong())

        applicationContext.close()
    }
}
