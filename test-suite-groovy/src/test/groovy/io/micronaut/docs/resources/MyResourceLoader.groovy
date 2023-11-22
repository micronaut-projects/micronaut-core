package io.micronaut.docs.resources

import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.IOUtils
import io.micronaut.core.io.ResourceResolver
import jakarta.inject.Singleton

@Requires(property='spec.name', value='ResourceLoaderSpec')
// tag::class[]
@Singleton
class MyResourceLoader {

    private final ResourceResolver resourceResolver // <1>

    MyResourceLoader(ResourceResolver resourceResolver) { // <1>
        this.resourceResolver = resourceResolver
    }

    Optional<String> getClasspathResourceAsText(String path) throws Exception {
        Optional<URL> url = resourceResolver.getResource('classpath:' + path) // <2>
        if (url.isPresent()) {
            return Optional.of(IOUtils.readText(new BufferedReader(new InputStreamReader(url.get().openStream()))))  // <3>
        } else {
            return Optional.empty()
        }
    }
}
// end::class[]
