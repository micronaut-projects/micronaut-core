package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import spock.lang.Specification

import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class ConfigurationLoadStrategySpec extends Specification {

    void "default strategy fails on duplicate configuration resources"() {
        given:
        def jars = duplicateJars(
                "app-1.0.jar",
                "lib-1.0.jar",
                "application.properties",
                "foo=app\n",
                "foo=lib\n"
        )

        when:
        try (URLClassLoader cl = new URLClassLoader(jars*.toUri()*.toURL() as URL[], getClass().classLoader)) {
            ApplicationContext.builder(cl).start()
        }

        then:
        def e = thrown(ConfigurationException)
        e.message.contains("Duplicate configuration resource 'application.properties'")
        e.message.contains("app-1.0.jar")
        e.message.contains("lib-1.0.jar")
    }

    void "FIRST_MATCH uses first match and can disable warning"() {
        given:
        def jars = duplicateJars(
                "app-1.0.jar",
                "lib-1.0.jar",
                "application.properties",
                "foo=app\n",
                "foo=lib\n"
        )

        when:
        String value
        try (URLClassLoader cl = new URLClassLoader(jars*.toUri()*.toURL() as URL[], getClass().classLoader);
             ApplicationContext ctx = ApplicationContext.builder(cl)
                     .configurationLoadingStrategy { b ->
                         b.type(ConfigurationLoadStrategyType.FIRST_MATCH)
                         b.warnOnDuplicates(false)
                     }
                     .start()) {

            value = ctx.environment.getProperty("foo", String).orElse(null)
        }

        then:
        value == "app"
    }

    void "MERGE_ALL merges duplicates in classpath order"() {
        given:
        def jars = duplicateJars(
                "app-1.0.jar",
                "lib-1.0.jar",
                "application.properties",
                "foo=app\nappOnly=yes\n",
                "foo=lib\nlibOnly=yes\n"
        )

        when:
        Map<String, Object> props
        try (URLClassLoader cl = new URLClassLoader(jars*.toUri()*.toURL() as URL[], getClass().classLoader);
             ApplicationContext ctx = ApplicationContext.builder(cl)
                     .configurationLoadingStrategy { b ->
                         b.type(ConfigurationLoadStrategyType.MERGE_ALL)
                     }
                     .start()) {
            props = [
                    foo: ctx.environment.getProperty("foo", String).orElse(null),
                    appOnly: ctx.environment.getProperty("appOnly", String).orElse(null),
                    libOnly: ctx.environment.getProperty("libOnly", String).orElse(null)
            ]
        }

        then:
        props.foo == "lib"
        props.appOnly == "yes"
        props.libOnly == "yes"
    }

    void "MERGE_ALL mergeOrder can reorder by jar name"() {
        given:
        def jars = duplicateJars(
                "app-1.0.jar",
                "lib-1.0.jar",
                "application.properties",
                "foo=app\n",
                "foo=lib\n"
        )

        when:
        String value
        try (URLClassLoader cl = new URLClassLoader(jars*.toUri()*.toURL() as URL[], getClass().classLoader);
             ApplicationContext ctx = ApplicationContext.builder(cl)
                     .configurationLoadingStrategy { b ->
                         b.type(ConfigurationLoadStrategyType.MERGE_ALL)
                         b.mergeOrder("lib-.*\\.jar", "app-.*\\.jar")
                     }
                     .start()) {

            value = ctx.environment.getProperty("foo", String).orElse(null)
        }

        then:
        value == "app"
    }

    void "mergeOrder is rejected when strategy type is not MERGE_ALL"() {
        given:
        def jars = duplicateJars(
                "app-1.0.jar",
                "lib-1.0.jar",
                "application.properties",
                "foo=app\n",
                "foo=lib\n"
        )

        when:
        try (URLClassLoader cl = new URLClassLoader(jars*.toUri()*.toURL() as URL[], getClass().classLoader)) {
            ApplicationContext.builder(cl)
                    .configurationLoadingStrategy { b ->
                        b.mergeOrder("app-.*\\.jar")
                    }
        }

        then:
        thrown(ConfigurationException)
    }

    void "duplicates are detected for environment-specific resources too"() {
        given:
        Path dir = Files.createTempDirectory("mn-config-strategy")
        Path jar1 = createJar(dir.resolve("app-1.0.jar"), [
                ("application-test.properties"): "bar=app\n"
        ])
        Path jar2 = createJar(dir.resolve("lib-1.0.jar"), [
                ("application-test.properties"): "bar=lib\n"
        ])

        when:
        try (URLClassLoader cl = new URLClassLoader([jar1.toUri().toURL(), jar2.toUri().toURL()] as URL[], getClass().classLoader)) {
            ApplicationContext.builder(cl)
                    .environments("test")
                    .start()
        }

        then:
        def e = thrown(ConfigurationException)
        e.message.contains("application-test.properties")
    }

    private static List<Path> duplicateJars(String jar1Name,
                                           String jar2Name,
                                           String resourceName,
                                           String jar1Content,
                                           String jar2Content) {
        Path dir = Files.createTempDirectory("mn-config-strategy")
        Path jar1 = createJar(dir.resolve(jar1Name), [(resourceName): jar1Content])
        Path jar2 = createJar(dir.resolve(jar2Name), [(resourceName): jar2Content])
        return [jar1, jar2]
    }

    private static Path createJar(Path jarPath, Map<String, String> entries) {
        Files.createDirectories(jarPath.parent)
        jarPath.toFile().withOutputStream { os ->
            JarOutputStream jos = new JarOutputStream(os)
            try {
                entries.each { String name, String content ->
                    JarEntry entry = new JarEntry(name)
                    jos.putNextEntry(entry)
                    jos.write(content.getBytes(StandardCharsets.UTF_8))
                    jos.closeEntry()
                }
            } finally {
                jos.close()
            }
        }
        return jarPath
    }
}
