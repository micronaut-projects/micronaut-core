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

    void "a resource deleted from a live root is gone rather than served from the build output's copy, in every generation"() {
        given: "a live resource root and the build's copy of it, beside a resource only the build output holds"
        Path live = Files.createDirectories(project.resolve("src/main/resources"))
        Path views = Files.createDirectories(live.resolve("views"))
        Path index = views.resolve("index.html")
        Files.writeString(index, "live")
        Path partials = Files.createDirectories(views.resolve("partials"))
        Files.writeString(partials.resolve("a.html"), "a")
        Path output = Files.createDirectories(project.resolve("build/resources/main/views/partials"))
        Files.writeString(output.resolve("../index.html").normalize(), "stale")
        Files.writeString(output.resolve("a.html"), "stale a")
        Files.writeString(output.resolve("../generated.html").normalize(), "generated")
        DevClassLoader loader = new DevClassLoader(DevClassLoaderSpec.classLoader, [live], [project.resolve("build/resources/main")], project.resolve("generations"))

        expect: "the live file wins, and is the only copy listed"
        read(loader.current(), "views/index.html") == "live"
        Collections.list(loader.current().getResources("views/index.html")).size() == 1

        when: "the live file is deleted"
        Files.delete(index)

        then: "the stale copy is hidden, while a resource only the build output holds is served"
        loader.current().getResource("views/index.html") == null
        !loader.current().getResources("views/index.html").hasMoreElements()
        read(loader.current(), "views/generated.html") == "generated"

        when: "a directory is deleted as a whole, and a file created after startup is read and then deleted"
        Files.delete(partials.resolve("a.html"))
        Files.delete(partials)
        List<Path> under = loader.liveResources().knownUnder(partials)
        Path late = views.resolve("late.html")
        Files.writeString(late, "late")
        Files.writeString(project.resolve("build/resources/main/views/late.html"), "stale late")
        String lateContent = read(loader.current(), "views/late.html")
        Files.delete(late)

        then: "each file it held is known, and every one is gone"
        under == [partials.resolve("a.html").toAbsolutePath().normalize()]
        loader.current().getResource("views/partials/a.html") == null
        lateContent == "late"
        loader.current().getResource("views/late.html") == null

        when: "a file the watcher reported but nothing read is deleted, and the loader swaps"
        Path reported = views.resolve("reported.html")
        Files.writeString(reported, "reported")
        Files.writeString(project.resolve("build/resources/main/views/reported.html"), "stale reported")
        loader.liveResources().seen(reported)
        Files.delete(reported)
        loader.swap()

        then: "the new generation keeps them gone"
        loader.current().generation() == 2
        loader.current().getResource("views/index.html") == null
        loader.current().getResource("views/late.html") == null
        loader.current().getResource("views/reported.html") == null
        read(loader.current(), "views/generated.html") == "generated"

        when: "the live file is written again"
        Files.writeString(index, "back")

        then: "it is served live"
        read(loader.current(), "views/index.html") == "back"

        cleanup:
        loader?.current()?.close()
    }

    private static String read(ClassLoader loader, String name) {
        InputStream input = loader.getResourceAsStream(name)
        assert input != null : name
        input.withCloseable { new String(it.readAllBytes(), "UTF-8") }
    }

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

    void "after Class.forName through the facade, a swap still serves the new class to loadClass on the facade and to Class.forName on the current generation"() {
        given: "a compiled generation"
        Path src = Files.createDirectories(project.resolve("src"))
        Path classes = project.resolve("classes")
        Path source = src.resolve("example/Example.java")
        Files.createDirectories(source.parent)
        Files.writeString(source, 'package example; public class Example { public String value() { return "one"; } }')
        JavacSourceCompiler compiler = new JavacSourceCompiler()
        compiler.compile(new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, src)], [] as Set, [] as Set, true, [], [], classes, project.resolve("generated"), []))
        DevClassLoader loader = new DevClassLoader(getClass().classLoader, [classes], project.resolve("generations"))

        when: "the class is loaded by name with the facade as the initiating loader"
        Class<?> one = Class.forName("example.Example", true, loader)

        then:
        one.classLoader.is(loader.current())
        value(one) == "one"

        when: "the method changes and a new generation is swapped in"
        Files.writeString(source, 'package example; public class Example { public String value() { return "two"; } }')
        compiler.compile(new CompilationRequest(SourceKind.JAVA, [new SourceRoot(SourceKind.JAVA, src)], [source] as Set, [] as Set, false, [], [], classes, project.resolve("generated"), []))
        def retired = loader.swap()
        Class<?> viaFacade = loader.loadClass("example.Example")
        Class<?> viaGeneration = Class.forName("example.Example", true, loader.current())

        then: "loadClass on the facade does not answer with the class the JVM recorded the facade as initiating"
        !viaFacade.is(one)
        viaFacade.classLoader.is(loader.current())
        value(viaFacade) == "two"

        and: "Class.forName on the current generation answers the new class"
        viaGeneration.is(viaFacade)
        loader.isStale(one)

        and: "Class.forName on the facade answers from the JVM's record of what the facade initiated, which nothing can clear: the reason callers name the current generation"
        Class.forName("example.Example", true, loader).is(one)

        cleanup:
        retired?.close()
        loader.current().close()
    }

    private static Object value(Class<?> type) {
        type.getMethod("value").invoke(type.getDeclaredConstructor().newInstance())
    }
}
