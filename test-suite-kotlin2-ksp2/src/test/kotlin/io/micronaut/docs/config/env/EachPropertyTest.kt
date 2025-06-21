package io.micronaut.docs.config.env

import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import java.net.URI
import java.net.URISyntaxException

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
        beansOfType.size shouldBe 2 // <1>

        val firstConfig = applicationContext.getBean(
                DataSourceConfiguration::class.java,
                Qualifiers.byName("one") // <2>
        )

        firstConfig.url shouldBe URI("jdbc:mysql://localhost/one")
        // end::beans[]
        applicationContext.close()
    }

    @Test
    fun testEachPropertyList() {
        val limits: MutableList<Map<*, *>> = ArrayList()
        limits.add(mapOf("period" to "10s", "limit" to "1000"))
        limits.add(mapOf("period" to "1m", "limit" to "5000"))
        val applicationContext = ApplicationContext.run(
            mapOf("ratelimits" to listOf(
                mapOf("period" to "10s", "limit" to "1000"),
                mapOf("period" to "1m", "limit" to "5000"))))

        val beansOfType = applicationContext.streamOfType(RateLimitsConfiguration::class.java).toList()

        beansOfType.size shouldBe 2
        beansOfType[0].limit shouldBe 1000
        beansOfType[1].limit shouldBe 5000

        applicationContext.close()
    }
}
