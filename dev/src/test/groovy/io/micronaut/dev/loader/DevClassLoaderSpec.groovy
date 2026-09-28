/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.dev.loader

import io.micronaut.dev.change.OutputSnapshot
import io.micronaut.dev.compile.CompilationRequest
import io.micronaut.dev.compile.JavacSourceCompiler
import io.micronaut.dev.compile.SourceKind
import io.micronaut.dev.compile.SourceRoot
import io.micronaut.context.reload.ClassChange
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class DevClassLoaderSpec extends Specification {

    @TempDir
    Path project

    void "a swap retires the generation, marks its classes stale and serves the new classes and resources"() {
        given: "a compiled generation and a resource root"
        Path src = Files.createDirectories(project.resolve("src"))
        Path classes = project.resolve("classes")
        Path resources = Files.createDirectories(project.resolve("resources"))
        Files.writeString(resources.resolve("greeting.txt"), "hello")
        Path source = src.resolve("example/Greeter.java")
        Files.createDirectories(source.parent)
        Files.writeString(source, 'package example; public class Greeter { public String greet() { return "one"; } }')
        Path other = src.resolve("example/Other.java")
        Files.writeString(other, 'package example; public class Other { public String name() { return "old"; } }')
        JavacSourceCompiler compiler = new JavacSourceCompiler()
        compiler.compile(new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, src)], [] as Set, [] as Set, true, [], [], classes, project.resolve("generated"), []))
        DevClassLoader loader = new DevClassLoader(getClass().classLoader, [classes, resources], project.resolve("generations"))
        OutputSnapshot first = OutputSnapshot.of([classes, resources])

        expect: "generation one serves the class and the resource, parent-first"
        loader.generation() == 1
        loader.loadClass("java.lang.String") == String
        Class<?> one = loader.loadClass("example.Greeter")
        one.classLoader.is(loader.current())
        one.getMethod("greet").invoke(one.getDeclaredConstructor().newInstance()) == "one"
        loader.getResource("greeting.txt").text == "hello"
        !loader.isStale(one)
        loader.retiredLoaders().isEmpty()

        when: "the source changes and a new generation is swapped in"
        Files.writeString(source, 'package example; public class Greeter { public String greet() { return "two"; } }')
        Files.writeString(other, 'package example; public class Other { public String name() { return "new"; } }')
        compiler.compile(new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, src)], [source, other] as Set, [] as Set, false, [], [], classes, project.resolve("generated"), []))
        Files.writeString(resources.resolve("greeting.txt"), "hi")
        String resourceBeforeSwap = loader.getResource("greeting.txt").text
        def changes = first.diff(OutputSnapshot.of([classes, resources]))
        def retired = loader.swap()
        Class<?> two = loader.loadClass("example.Greeter")

        then: "the change set names the class and the resource"
        changes.classes == [new ClassChange("example.Greeter", ClassChange.Kind.MODIFIED), new ClassChange("example.Other", ClassChange.Kind.MODIFIED)]
        changes.changedResources == ["greeting.txt"] as Set

        and: "the new generation defines the class again, the old one is stale"
        loader.generation() == 2
        retired.generation() == 1
        !two.is(one)
        two.classLoader.is(loader.current())
        two.getMethod("greet").invoke(two.getDeclaredConstructor().newInstance()) == "two"
        loader.isStale(one)
        !loader.isStale(two)
        !loader.isStale(String)
        loader.retiredLoaders() == [retired] as Set
        loader.getResource("greeting.txt").text == "hi"

        and: "the running generation read its snapshot while the output changed underneath it"
        resourceBeforeSwap == "hello"

        and: "a class the retired generation resolves late comes from its snapshot, not from the new output"
        Class<?> lateOld = retired.loadClass("example.Other")
        lateOld.classLoader.is(retired)
        lateOld.getMethod("name").invoke(lateOld.getDeclaredConstructor().newInstance()) == "old"
        Class<?> lateNew = loader.loadClass("example.Other")
        lateNew.getMethod("name").invoke(lateNew.getDeclaredConstructor().newInstance()) == "new"
        Files.isDirectory(project.resolve("generations/1"))
        Files.isDirectory(project.resolve("generations/2"))

        when: "the retired generation is closed"
        retired.close()

        then: "its snapshot is gone, the current one stays"
        !Files.exists(project.resolve("generations/1"))
        Files.isDirectory(project.resolve("generations/2"))

        cleanup:
        loader.current().close()
    }
}
