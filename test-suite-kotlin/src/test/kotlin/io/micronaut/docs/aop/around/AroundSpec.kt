package io.micronaut.docs.aop.around

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.AnnotationSpec
import io.micronaut.context.ApplicationContext

class AroundSpec: AnnotationSpec() {

    // tag::test[]
    @Test
    fun testNotNull() {
        val applicationContext = ApplicationContext.run()
        val exampleBean = applicationContext.getBean(NotNullExample::class.java)

        val exception = shouldThrow<IllegalArgumentException> {
            exampleBean.doWork(null)
        }
        exception.message shouldBe "Null parameter [taskName] not allowed"
        applicationContext.close()
    }
    // end::test[]
}
