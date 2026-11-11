package io.micronaut.core.io.scan

import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Requires({ !os.windows })
class DefaultClassPathResourceLoaderSymlinkSpec extends Specification {

    @TempDir
    Path root

    URLClassLoader classLoader

    def setup() {
        Path classes = root.resolve("classes")
        Files.createDirectories(classes.resolve("public/assets"))
        Files.createDirectories(classes.resolve("private"))
        Files.write(classes.resolve("public/index.html"), "index".bytes)
        Files.write(classes.resolve("public/assets/app.js"), "app".bytes)
        Files.write(classes.resolve("private/key.txt"), "secret".bytes)
        Files.createSymbolicLink(classes.resolve("public/export"), Paths.get("../private"))
        Files.createSymbolicLink(classes.resolve("public/linked.js"), Paths.get("assets/app.js"))
        classLoader = new URLClassLoader([classes.toUri().toURL()] as URL[], (ClassLoader) null)
    }

    def cleanup() {
        classLoader?.close()
    }

    def 'a loader with a base path does not follow a symbolic link out of the base directory'() {
        given:
        def loader = new DefaultClassPathResourceLoader(classLoader).forBase("classpath:public")

        expect:
        !loader.getResource("export/key.txt").isPresent()
        !loader.getResourceAsStream("export/key.txt").isPresent()
        loader.getResources("export/key.txt").count() == 0
    }

    def 'a loader with a base path serves files and symbolic links inside the base directory'() {
        given:
        def loader = new DefaultClassPathResourceLoader(classLoader).forBase("classpath:public")

        expect:
        loader.getResource("index.html").isPresent()
        loader.getResourceAsStream("assets/app.js").get().text == "app"
        loader.getResourceAsStream("linked.js").get().text == "app"
    }

    def 'a loader without a base path keeps resolving resources through symbolic links'() {
        given:
        def loader = new DefaultClassPathResourceLoader(classLoader)

        expect:
        loader.getResourceAsStream("public/export/key.txt").get().text == "secret"
    }
}
