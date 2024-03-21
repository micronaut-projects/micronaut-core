package io.micronaut.docs.aop.around

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.AnnotationSpec
import io.micronaut.context.ApplicationContext

class PrivateFieldNotNullSpec: AnnotationSpec() {

    @Test
    fun testNotNull() {
        val applicationContext = ApplicationContext.run()
        val exampleBean = applicationContext.getBean(PrivateFieldNotNullExample::class.java)
        val work = exampleBean.doWork("work")
        work shouldBe "work"
        applicationContext.close()
    }
}
