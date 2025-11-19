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
import okio.buffer
import okio.sink
import org.intellij.lang.annotations.Language

/**
 * A source file for the [KotlinCompilation]
 */
abstract class SourceFile {
    internal abstract fun writeTo(dir: File): File

    companion object {
        /**
         * Create a new Java source file for the compilation when the compilation is run
         */
        fun java(name: String, @Language("java") contents: String, trimIndent: Boolean = true): SourceFile {
            require(File(name).hasJavaFileExtension())
            val finalContents = if (trimIndent) contents.trimIndent() else contents
            return new(name, finalContents)
        }

        /**
         * Create a new Kotlin source file for the compilation when the compilation is run
         */
        fun kotlin(name: String, @Language("kotlin") contents: String, trimIndent: Boolean = true): SourceFile {
            require(File(name).hasKotlinFileExtension())
            val finalContents = if (trimIndent) contents.trimIndent() else contents
            return new(name, finalContents)
        }

        /**
         * Create a new source file for the compilation when the compilation is run
         */
        fun new(name: String, contents: String) = object : SourceFile() {
            override fun writeTo(dir: File): File {
                val file = dir.resolve(name)
                file.parentFile.mkdirs()
                file.createNewFile()

                file.sink().buffer().use {
                    it.writeUtf8(contents)
                }

                return file
            }
        }

        /**
         * Compile an existing source file
         */
        @Deprecated("This will not work reliably with KSP, use `new` instead")
        fun fromPath(path: File) = object : SourceFile() {
            init {
                require(path.isFile)
            }

            override fun writeTo(dir: File): File = path
        }
    }
}
