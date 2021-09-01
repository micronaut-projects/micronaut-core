package io.micronaut.docs.config.converters

import io.kotest.core.spec.style.AnnotationSpec
import io.micronaut.context.ApplicationContext
import org.junit.Assert.assertEquals
import java.time.LocalDate

//tag::configSpec[]
class MyConfigurationPropertiesSpec : AnnotationSpec() {

    //tag::runContext[]
    lateinit var ctx: ApplicationContext

    @BeforeEach
    fun setup() {
        ctx = ApplicationContext.run(
            mapOf(
                "myapp.updatedAt" to mapOf( // <1>
                    "day" to 28,
                    "month" to 10,
                    "year" to 1982
                )
            )
        )
    }

    @AfterEach
    fun teardown() {
        ctx?.close()
    }
    //end::runContext[]

    @Test
    fun testConvertDateFromMap() {
        val props = ctx.getBean(MyConfigurationProperties::class.java)

        val expectedDate = LocalDate.of(1982, 10, 28)
        assertEquals(expectedDate, props.updatedAt)
    }
}
//end::configSpec[]
