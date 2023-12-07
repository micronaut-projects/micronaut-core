package io.micronaut.docs.resources;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.io.ResourceResolver;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Optional;

@Requires(property="spec.name", value="ResourceLoaderTest")
// tag::class[]
@Singleton
public class MyResourceLoader {

    private final ResourceResolver resourceResolver;

    public MyResourceLoader(ResourceResolver resourceResolver) {  // <1>
        this.resourceResolver = resourceResolver;
    }

    public Optional<String> getClasspathResourceAsText(String path) throws Exception {
        Optional<URL> url = resourceResolver.getResource("classpath:" + path); // <2>
        if (url.isPresent()) {
            return Optional.of(IOUtils.readText(new BufferedReader(new InputStreamReader(url.get().openStream())))); // <3>
        } else {
            return Optional.empty();
        }
    }
}
// end::class[]
