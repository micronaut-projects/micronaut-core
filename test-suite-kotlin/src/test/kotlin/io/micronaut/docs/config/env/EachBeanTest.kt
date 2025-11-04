package io.micronaut.docs.config.env

import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.docs.config.env.DataSourceFactory.DataSource
import io.micronaut.inject.qualifiers.Qualifiers
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
        beansOfType.size shouldBe 2 // <1>

        val firstConfig = applicationContext.getBean(
                DataSource::class.java,
                Qualifiers.byName("one") // <2>
        )
        // end::beans[]

        firstConfig.shouldNotBeNull()

        applicationContext.close()
    }
}
