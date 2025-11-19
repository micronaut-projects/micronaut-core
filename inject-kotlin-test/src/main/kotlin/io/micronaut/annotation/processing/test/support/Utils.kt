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

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.Charset
import javax.lang.model.SourceVersion
import okio.Buffer

internal fun <E> MutableCollection<E>.addAll(vararg elems: E) = addAll(elems)

internal fun getJavaHome(): File {
    val path = System.getProperty("java.home")
        ?: System.getenv("JAVA_HOME")
        ?: throw IllegalStateException("no java home found")

    return File(path).also { check(it.isDirectory) }
}

internal val processJdkHome by lazy {
    if(isJdk9OrLater())
        getJavaHome()
    else
        getJavaHome().parentFile
}

/** Checks if the JDK of the host process is version 9 or later */
internal fun isJdk9OrLater(): Boolean
        = SourceVersion.latestSupported().compareTo(SourceVersion.RELEASE_8) > 0

internal fun File.listFilesRecursively(): List<File> = walkTopDown()
    .filter { it.isFile }
    .toList()

internal fun File.hasKotlinFileExtension() = hasFileExtension(listOf("kt", "kts"))

internal fun File.hasJavaFileExtension() = hasFileExtension(listOf("java"))

internal fun File.hasFileExtension(extensions: List<String>)
        = extensions.any{ it.equals(extension, ignoreCase = true) }

internal fun URLClassLoader.addUrl(url: URL) {
    val addUrlMethod = URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java)
    addUrlMethod.isAccessible = true
    addUrlMethod.invoke(this, url)
}

internal inline fun <T> withSystemProperty(key: String, value: String, f: () -> T): T
        = withSystemProperties(mapOf(key to value), f)


internal inline fun <T> withSystemProperties(properties: Map<String, String>, f: () -> T): T {
    val previousProperties = mutableMapOf<String, String?>()

    for ((key, value) in properties) {
        previousProperties[key] = System.getProperty(key)
        System.setProperty(key, value)
    }

    try {
        return f()
    } finally {
        for ((key, value) in previousProperties) {
            if (value != null)
                System.setProperty(key, value)
        }
    }
}

internal inline fun <R> withSystemOut(stream: PrintStream, crossinline f: () -> R): R {
    System.setOut(stream)
    val ret = f()
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out)))
    return ret
}

internal inline fun <R> captureSystemOut(crossinline f: () -> R): Pair<R, String> {
    val systemOutBuffer = Buffer()
    val ret = withSystemOut(PrintStream(systemOutBuffer.outputStream()), f)
    return Pair(ret, systemOutBuffer.readString(Charset.defaultCharset()))
}

internal fun File.existsOrNull(): File? = if (exists()) this else null
