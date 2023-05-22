package io.micronaut.docs.aop.introduction

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.AnnotationSpec
import io.micronaut.context.ApplicationContext

class IntroductionSpec: AnnotationSpec() {

    @Test
    fun testStubIntroduction() {
        val applicationContext = ApplicationContext.run()

        // tag::test[]
        val stubExample = applicationContext.getBean(StubExample::class.java)

        stubExample.number.shouldBe(10)
        stubExample.date.shouldBe(null)
        // end::test[]

        applicationContext.stop()
    }
}
