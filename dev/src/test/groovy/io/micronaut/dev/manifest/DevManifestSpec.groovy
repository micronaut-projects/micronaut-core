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
package io.micronaut.dev.manifest

import io.micronaut.context.reload.ReloadStrategy
import io.micronaut.context.reload.ResourceKind
import io.micronaut.dev.compile.CompileMode
import io.micronaut.dev.compile.SourceKind
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class DevManifestSpec extends Specification {

    @TempDir
    Path dir

    void "a manifest resolves its paths, lists, argument files and per-language settings"() {
        given:
        Files.writeString(dir.resolve("runtime.argfile"), "lib/a.jar\n\n# comment\nlib/b.jar\n")
        Files.writeString(dir.resolve("javac.args"), "-Xlint:deprecation\n--enable-preview\n")
        Path file = dir.resolve("dev.properties")
        Files.writeString(file, """
            micronaut.dev.main-class=example.Application
            micronaut.dev.strategy=restart
            micronaut.dev.runtime-classpath=@runtime.argfile
            micronaut.dev.reloadable=build/classes/java/main,build/resources/main
            micronaut.dev.compile-classpath=lib/a.jar${File.pathSeparator}lib/b.jar
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.sources.kotlin=src/main/kotlin,src/main/kotlin2
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.resources.views=src/main/resources/views
            micronaut.dev.compile.kotlin.mode=build-tool
            micronaut.dev.compile.java.options=-parameters,--release,21,-Xlint:deprecation
            micronaut.dev.compile.kotlin.options=@javac.args
            micronaut.dev.compile.java.output=build/classes/java/main
            micronaut.dev.build-tool=gradle
            micronaut.dev.build-tool.trigger=build/micronaut-dev/reload
            micronaut.dev.retain=javax.sql.DataSource, org.graalvm.polyglot.Engine
            micronaut.dev.livereload.enabled=false
            micronaut.dev.livereload.port=36000
        """.stripIndent())

        when:
        DevManifest manifest = DevManifest.load(file)

        then:
        manifest.mainClass() == "example.Application"
        manifest.projectDir() == dir.toAbsolutePath().normalize()
        manifest.strategy() == ReloadStrategy.RESTART
        manifest.runtimeClasspath() == [dir.resolve("lib/a.jar"), dir.resolve("lib/b.jar")]
        manifest.reloadableRoots() == [dir.resolve("build/classes/java/main"), dir.resolve("build/resources/main")]
        manifest.compileClasspath() == [dir.resolve("lib/a.jar"), dir.resolve("lib/b.jar")]
        manifest.processorPath().isEmpty()
        manifest.sourceRoots(SourceKind.JAVA)*.path() == [dir.resolve("src/main/java")]
        manifest.sourceRoots(SourceKind.KOTLIN)*.path() == [dir.resolve("src/main/kotlin"), dir.resolve("src/main/kotlin2")]
        manifest.resourceRoots().find { it.kind() == ResourceKind.VIEWS }.path() == dir.resolve("src/main/resources/views")
        manifest.compileMode(SourceKind.JAVA) == CompileMode.EMBEDDED
        manifest.compileMode(SourceKind.KOTLIN) == CompileMode.BUILD_TOOL
        manifest.isIncremental()
        manifest.compileOptions(SourceKind.JAVA) == ["-parameters", "--release", "21", "-Xlint:deprecation"]
        manifest.compileOptions(SourceKind.KOTLIN) == ["-Xlint:deprecation", "--enable-preview"]
        manifest.classOutput(SourceKind.JAVA) == dir.resolve("build/classes/java/main")
        manifest.classOutput(SourceKind.GROOVY) == dir.resolve("build/classes/java/main")
        manifest.generatedSources(SourceKind.JAVA) == dir.resolve("build/classes/java/generated-sources-java")
        manifest.buildTool() == "gradle"
        manifest.buildToolTrigger() == dir.resolve("build/micronaut-dev/reload")
        manifest.retain() == ["javax.sql.DataSource", "org.graalvm.polyglot.Engine"]
        !manifest.liveReload().isEnabled(true)
        manifest.liveReload().port() == 36000
        manifest.liveReload().injectScript()
    }

    void "the defaults apply and livereload is automatic"() {
        given:
        Properties properties = new Properties()
        properties.setProperty("micronaut.dev.main-class", "example.Application")
        properties.setProperty("micronaut.dev.reloadable", "classes")

        when:
        DevManifest manifest = DevManifest.of(dir, properties)

        then:
        manifest.strategy() == ReloadStrategy.AUTO
        manifest.compileMode(SourceKind.JAVA) == CompileMode.EMBEDDED
        manifest.liveReload().isEnabled(true)
        !manifest.liveReload().isEnabled(false)
        manifest.liveReload().port() == 35729
    }

    void "a manifest without a main class or a reloadable root is refused"() {
        when:
        DevManifest.of(dir, new Properties())

        then:
        thrown(IllegalArgumentException)

        when:
        Properties properties = new Properties()
        properties.setProperty("micronaut.dev.main-class", "example.Application")
        DevManifest.of(dir, properties)

        then:
        thrown(IllegalArgumentException)
    }
}
