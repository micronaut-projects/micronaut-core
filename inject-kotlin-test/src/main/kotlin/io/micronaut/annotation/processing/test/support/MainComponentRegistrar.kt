/*
 * Copyright 2017-2016 original authors
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

import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
internal class MainComponentRegistrar : ComponentRegistrar {

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        // Handle unset parameters gracefully because this plugin may be accidentally called by other tools that
        // discover it on the classpath (for example the kotlin jupyter kernel).
        if (threadLocalParameters.get() == null) {
            System.err.println("WARNING: MainComponentRegistrar::registerProjectComponents accessed before thread local parameters have been set")
            return
        }

        val parameters = threadLocalParameters.get()

        /*
        The order of registering plugins here matters. If the kapt plugin is registered first, then
        it will be executed first and any changes made to the AST by later plugins won't apply to the
        generated stub files and thus won't be visible to any annotation processors. So we decided
        to register third-party plugins before kapt and hope that it works, although we don't
        know for sure if that is the correct way.
         */
        parameters.compilerPlugins.forEach { componentRegistrar->
            componentRegistrar.registerProjectComponents(project,configuration)
        }

    }

    companion object {
        /** This compiler plugin is instantiated by K2JVMCompiler using
         *  a service locator. So we can't just pass parameters to it easily.
         *  Instead, we need to use a thread-local global variable to pass
         *  any parameters that change between compilations
         */
        val threadLocalParameters: ThreadLocal<ThreadLocalParameters> = ThreadLocal()
    }

    data class ThreadLocalParameters @OptIn(ExperimentalCompilerApi::class) constructor(
        val compilerPlugins: List<ComponentRegistrar>
    )
}
