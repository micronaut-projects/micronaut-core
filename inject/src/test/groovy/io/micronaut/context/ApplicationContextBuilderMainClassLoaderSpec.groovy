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

    void "a main class whose loader does not see the default loader's classes leaves the default loader"() {
        given: "a main class of a parent of the default loader, and one of a loader beside it"
        Class<?> parentMain = java.sql.Driver
        def beside = new io.micronaut.inject.annotation.AnnotationTypeClassLoaderSpec.OwnCopy()
        Class<?> besideMain = beside.define(io.micronaut.inject.annotation.Reloaded.name)

        expect:
        parentMain.classLoader != null
        isAncestor(parentMain.classLoader, ApplicationContext.classLoader)
        ApplicationContext.builder().mainClass(parentMain).classLoader == ApplicationContext.classLoader
        ApplicationContext.builder().mainClass(besideMain).classLoader == ApplicationContext.classLoader

        and: "the main class still supplies the package to scan"
        ApplicationContext.builder().mainClass(besideMain).@packages.contains('io.micronaut.inject.annotation')

    }

    void "the last main class decides the loader, each checked against the default loader"() {
        given: "main classes of two sibling children of the default loader"
        GroovyClassLoader first = new GroovyClassLoader(getClass().classLoader)
        GroovyClassLoader second = new GroovyClassLoader(getClass().classLoader)
        Class<?> firstMain = first.parseClass('package example.first; class Application {}')
        Class<?> secondMain = second.parseClass('package example.second; class Application {}')

        expect:
        ApplicationContext.builder().mainClass(firstMain).mainClass(secondMain).classLoader == secondMain.classLoader
        secondMain.classLoader != firstMain.classLoader
        ApplicationContext.builder().mainClass(firstMain).mainClass(java.sql.Driver).classLoader == ApplicationContext.classLoader

        cleanup:
        first.close()
        second.close()
    }

    private static boolean isAncestor(ClassLoader ancestor, ClassLoader loader) {
        for (ClassLoader current = loader; current != null; current = current.parent) {
            if (current.is(ancestor)) {
                return true
            }
        }
        return false
    }
}
