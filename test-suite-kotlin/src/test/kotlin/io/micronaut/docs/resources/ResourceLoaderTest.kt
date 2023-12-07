package io.micronaut.docs.resources

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ResourceLoaderTest {

    @Test
    @Throws(Exception::class)
    fun testExampleForResourceResolver() {

        val applicationContext = ApplicationContext.run(
            mapOf("spec.name" to "ResourceLoaderTest"),
            "test"
        )
        val myResourceLoader = applicationContext.getBean(MyResourceLoader::class.java)

        Assertions.assertNotNull(myResourceLoader)
        val text = myResourceLoader.getClasspathResourceAsText("hello.txt")
        Assertions.assertTrue(text.isPresent)
        Assertions.assertEquals("Hello!", text.get().trim { it <= ' ' })

        applicationContext.stop()
    }
}
