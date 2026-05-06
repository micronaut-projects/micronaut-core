import java

from micronaut.context.annotation import Requires
from jakarta.inject import Singleton

BufferedReader = java.type("java.io.BufferedReader")
InputStreamReader = java.type("java.io.InputStreamReader")
IOUtils = java.type("io.micronaut.core.io.IOUtils")
Optional = java.type("java.util.Optional")
ResourceResolver = java.type("io.micronaut.core.io.ResourceResolver")


@Requires(property="spec.name", value="ResourceLoaderTest")
# tag::class[]
@Singleton
class MyResourceLoader:

    def __init__(self, resourceResolver: ResourceResolver):  # <1>
        self.resourceResolver = resourceResolver

    def getClasspathResourceAsText(self, path: str) -> Optional:
        url = self.resourceResolver.getResource("classpath:" + path)  # <2>
        if url.isPresent():
            return Optional.of(
                IOUtils.readText(BufferedReader(InputStreamReader(url.get().openStream())))  # <3>
            )
        else:
            return Optional.empty()
# end::class[]
