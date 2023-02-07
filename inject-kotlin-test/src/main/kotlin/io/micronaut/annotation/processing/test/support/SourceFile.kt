package io.micronaut.annotation.processing.test.support

import okio.buffer
import okio.sink
import org.intellij.lang.annotations.Language
import java.io.File

/**
 * A source file for the [KotlinCompilation]
 */
abstract class SourceFile {
    internal abstract fun writeIfNeeded(dir: File): File

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
            override fun writeIfNeeded(dir: File): File {
                val file = dir.resolve(name)
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
        fun fromPath(path: File) = object : SourceFile() {
            init {
                require(path.isFile)
            }

            override fun writeIfNeeded(dir: File): File = path
        }
    }
}
