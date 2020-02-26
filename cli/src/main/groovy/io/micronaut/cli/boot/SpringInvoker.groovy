/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.boot

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.springframework.boot.cli.command.CommandFactory
import org.springframework.boot.cli.command.CommandRunner

/**
 * Allows invocation of Spring commands from command scripts
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton(strict = false)
@CompileStatic
class SpringInvoker {

    CommandRunner runner = new CommandRunner("spring");

    private SpringInvoker() {
        addServiceLoaderCommands(runner)
    }

    private static void addServiceLoaderCommands(CommandRunner runner) {
        ServiceLoader<CommandFactory> factories = ServiceLoader.load(
            CommandFactory.class, Thread.currentThread().contextClassLoader)
        factories.each { CommandFactory factory ->
            runner.addCommands factory.getCommands()
        }
    }

    @Override
    Object invokeMethod(String name, Object args) {
        if (args instanceof Object[]) {

            List<String> argList = [name]
            argList.addAll(((Object[]) args).collect() { it.toString() })

            def currentThread = Thread.currentThread()
            def existing = currentThread.contextClassLoader
            try {
                currentThread.contextClassLoader = new Slf4jBindingAwareClassLoader()
                return runner.runAndHandleErrors(argList as String[])
            } finally {
                currentThread.contextClassLoader = existing
            }
        }
        return null
    }

    @InheritConstructors
    static class Slf4jBindingAwareClassLoader extends URLClassLoader {
        @Override
        Enumeration<URL> getResources(String name) throws IOException {
            if ("org/slf4j/impl/StaticLoggerBinder.class" == name) {
                def resources = super.getResources(name)
                def oneRes = (URL) resources.find() { URL url -> !url.toString().contains('slf4j-simple') }
                if (oneRes) {

                    return new Enumeration<URL>() {
                        URL current = oneRes

                        @Override
                        boolean hasMoreElements() {
                            current != null
                        }

                        @Override
                        URL nextElement() {
                            URL i = current
                            current = null
                            return i
                        }
                    }
                } else {
                    return resources
                }
            } else {
                return super.getResources(name)
            }
        }
    }
}
