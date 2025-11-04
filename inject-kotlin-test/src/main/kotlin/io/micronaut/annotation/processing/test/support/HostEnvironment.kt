/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.annotation.processing.test.support

import io.github.classgraph.ClassGraph
import java.io.File

/**
 * Utility object to provide everything we might discover from the host environment.
 */
internal object HostEnvironment {
    val classpath by lazy {
        getHostClasspaths()
    }

    val kotlinStdLibJar: File? by lazy {
        findInClasspath(kotlinDependencyRegex("(kotlin-stdlib|kotlin-runtime)"))
    }

    val kotlinStdLibCommonJar: File? by lazy {
        findInClasspath(kotlinDependencyRegex("kotlin-stdlib-common"))
    }

    val kotlinStdLibJdkJar: File? by lazy {
        findInClasspath(kotlinDependencyRegex("kotlin-stdlib-jdk[0-9]+"))
    }

    val kotlinStdLibJsJar: File? by default {
        findInClasspath(kotlinDependencyRegex("kotlin-stdlib-js"))
    }

    val kotlinReflectJar: File? by lazy {
        findInClasspath(kotlinDependencyRegex("kotlin-reflect"))
    }

    val kotlinScriptRuntimeJar: File? by lazy {
        findInClasspath(kotlinDependencyRegex("kotlin-script-runtime"))
    }

    val toolsJar: File? by lazy {
        findInClasspath(Regex("tools.jar"))
    }

    private fun kotlinDependencyRegex(prefix: String): Regex {
        return Regex("$prefix(-[0-9]+\\.[0-9]+(\\.[0-9]+)?)([-0-9a-zA-Z]+)?\\.jar")
    }

    /** Tries to find a file matching the given [regex] in the host process' classpath */
    private fun findInClasspath(regex: Regex): File? {
        val jarFile = classpath.firstOrNull { classpath ->
            classpath.name.matches(regex)
            //TODO("check that jar file actually contains the right classes")
        }
        return jarFile
    }

    /** Returns the files on the classloader's classpath and modulepath */
    private fun getHostClasspaths(): List<File> {
        val classGraph = ClassGraph()
            .enableSystemJarsAndModules()
            .removeTemporaryFilesAfterScan()

        val classpaths = classGraph.classpathFiles
        val modules = classGraph.modules.mapNotNull { it.locationFile }

        return (classpaths + modules).distinctBy(File::getAbsolutePath)
    }
}
