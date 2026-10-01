package io.micronaut.docs.resources

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResourceLoaderTest {

    @Test
    fun testExampleForResourceResolver() {
        ApplicationContext.run(mapOf("spec.name" to "ResourceLoaderTest"), "test").use { applicationContext ->
            val myResourceLoader = applicationContext.getBean(MyResourceLoader::class.java)

            assertNotNull(myResourceLoader)
            val text = myResourceLoader.getClasspathResourceAsText("hello.txt")
            assertTrue(text.isPresent)
            assertEquals("Hello!", text.get().trim())
        }
    }
}
