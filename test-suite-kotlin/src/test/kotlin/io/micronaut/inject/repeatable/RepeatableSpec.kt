package io.micronaut.inject.repeatable

import io.micronaut.context.ApplicationContext
import junit.framework.TestCase

class RepeatableSpec: TestCase() {

    fun testBeanIsNotAvailable() {
        val context = ApplicationContext.run()
        TestCase.assertFalse(context.containsBean(MultipleRequires::class.java))
        context.close()
    }

    fun testBeanIsNotAvailable2() {
        val context = ApplicationContext.run(hashMapOf("foo" to "true") as Map<String, Any>?)
        TestCase.assertFalse(context.containsBean(MultipleRequires::class.java))
        context.close()
    }

    fun testBeanIsAvailable() {
        val context = ApplicationContext.run(hashMapOf("foo" to "true", "bar" to "y") as Map<String, Any>?)
        TestCase.assertTrue(context.containsBean(MultipleRequires::class.java))
        context.close()
    }
}