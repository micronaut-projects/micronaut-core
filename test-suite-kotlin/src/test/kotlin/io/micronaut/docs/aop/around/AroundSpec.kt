package io.micronaut.docs.aop.around

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.AnnotationSpec
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

    // tag::resultinline[]
    @Test
    fun testNotNullResultInline() {
        val applicationContext = ApplicationContext.run()
        val exampleBean = applicationContext.getBean(NotNullResultInnerExample::class.java)

        val exception = shouldThrow<IllegalArgumentException> {
            exampleBean.doWork(null)
        }
        exception.message shouldBe "Null parameter [taskName] not allowed"

        val taskName = exampleBean.doWork("test").getOrNull()

        taskName shouldBe "test"

        applicationContext.close()
    }
    // end::resultinline[]

    // tag::myinline[]
    @Test
    fun testNotNullMyInline() {
        val applicationContext = ApplicationContext.run()
        val exampleBean = applicationContext.getBean(NotNullMyInlineInnerExample::class.java)

        val exception1 = shouldThrow<IllegalArgumentException> {
            exampleBean.doWork(null)
        }
        exception1.message shouldBe "Null parameter [taskName] not allowed"

        val exception2 = shouldThrow<IllegalArgumentException> {
            exampleBean.doWork("")
        }
        exception2.message shouldBe "Should not be empty"

        val taskName = exampleBean.doWork("test").task
        taskName shouldBe "test"

        applicationContext.close()
    }
    // end::myinline[]
}
