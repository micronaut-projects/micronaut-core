/*
 * Copyright 2017-2018 original authors
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

import java.lang.reflect.Method
import javax.tools.JavaCompiler
import javax.tools.ToolProvider


/**
 * ToolProvider has no synchronization internally, so if we don't synchronize from the outside we
 * could wind uploading the compiler classes multiple times from different class loaders.
 */
internal object SynchronizedToolProvider {
    private var getPlatformClassLoaderMethod: Method? = null

    val systemJavaCompiler: JavaCompiler
        get() {
            val compiler = synchronized(ToolProvider::class.java) {
                ToolProvider.getSystemJavaCompiler()
            }

            check(compiler != null) { "System java compiler is null! Are you running without JDK?" }
            return compiler
        }

    init {
        if (isJdk9OrLater()) {
            try {
                getPlatformClassLoaderMethod = ClassLoader::class.java.getMethod("getPlatformClassLoader")
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            }

        }
    }
}
