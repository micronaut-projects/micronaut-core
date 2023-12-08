package io.micronaut.docs.resources

import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.IOUtils
import io.micronaut.core.io.ResourceResolver
import jakarta.inject.Singleton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

@Requires(property = "spec.name", value = "ResourceLoaderTest")
// tag::class[]
@Singleton
class MyResourceLoader(private val resourceResolver: ResourceResolver) { // <1>

    @Throws(Exception::class)
    fun getClasspathResourceAsText(path: String): Optional<String> {
        val url = resourceResolver.getResource("classpath:$path") // <2>
        return if (url.isPresent) {
            Optional.of(IOUtils.readText(BufferedReader(InputStreamReader(url.get().openStream()))))  // <3>
        } else {
            Optional.empty()
        }
    }
}
// end::class[]
