package io.micronaut.kotlin.processing.ksp

import io.micronaut.annotation.processing.test.KotlinCompiler
import spock.lang.Specification

import java.nio.file.Files
import java.util.stream.Collectors

class InheritedInnerClassesSpec extends Specification {

    void "test inherited nested classes of a type from another compilation are not visited again"() {
        when:
        def files = generatedFiles("""
package other

import io.micronaut.core.annotation.Introspected
import io.micronaut.kotlin.processing.innerclasses.Api

@Introspected
class Impl(val x: String) : Api
""")

        then:
        files.contains('classes/other/$Impl$Introspection.class')
        !files.any { it.contains('$Api$Nested$Introspection') }
    }

    void "test declared nested classes are visited"() {
        when:
        def files = generatedFiles("""
package other

import io.micronaut.core.annotation.Introspected

class Outer {
    @Introspected
    class Nested(val name: String)
}
""")

        then:
        files.contains('classes/other/$Outer$Nested$Introspection.class')
    }

    private static Set<String> generatedFiles(String source) {
        def pair = KotlinCompiler.compile('Source', source, { })
        def root = pair.component1().component1().workingDir.toPath().resolve("ksp")
        try (def paths = Files.walk(root)) {
            return paths.filter { Files.isRegularFile(it) }.map { root.relativize(it).toString() }.collect(Collectors.toSet())
        }
    }
}
