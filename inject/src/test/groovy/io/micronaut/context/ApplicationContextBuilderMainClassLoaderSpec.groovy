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
package io.micronaut.context

import spock.lang.Specification

class ApplicationContextBuilderMainClassLoaderSpec extends Specification {

    void "the main class's loader becomes the context loader unless one was chosen explicitly"() {
        given:
        GroovyClassLoader child = new GroovyClassLoader(getClass().classLoader)
        Class<?> mainClass = child.parseClass('package example.app; class Application {}')
        URLClassLoader explicit = new URLClassLoader(new URL[0], getClass().classLoader)

        expect: "the builder's own loader is only the default"
        ApplicationContext.builder().classLoader == ApplicationContext.classLoader

        and: "the main class supplies the loader"
        mainClass.classLoader != ApplicationContext.classLoader
        ApplicationContext.builder().mainClass(mainClass).classLoader == mainClass.classLoader

        and: "an explicit loader wins, whichever order it was set in"
        ApplicationContext.builder().classLoader(explicit).mainClass(mainClass).classLoader == explicit
        ApplicationContext.builder().mainClass(mainClass).classLoader(explicit).classLoader == explicit

        cleanup:
        explicit.close()
        child.close()
    }
}
