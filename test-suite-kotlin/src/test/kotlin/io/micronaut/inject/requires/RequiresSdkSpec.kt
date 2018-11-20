package io.micronaut.inject.requires

import io.micronaut.context.ApplicationContext
import junit.framework.TestCase

class RequiresSdkSpec: TestCase() {

    fun testRequiresKotlinSDKworks() {
        val context = ApplicationContext.run()
        TestCase.assertFalse(context.containsBean(RequiresFuture::class.java))
        TestCase.assertTrue(context.containsBean(RequiresOld::class.java))
        context.close()
    }

}