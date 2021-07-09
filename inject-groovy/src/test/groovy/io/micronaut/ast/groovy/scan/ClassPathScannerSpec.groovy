/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.ast.groovy.scan

import groovy.transform.AutoClone
import io.micronaut.ast.groovy.scan.nested.Foo2
import io.micronaut.ast.groovy.scan2.Foo3
import org.codehaus.groovy.transform.GroovyASTTransformation
import spock.lang.Specification

import java.util.stream.Collectors

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ClassPathScannerSpec extends Specification {

    void "test scan classpath for annotated classes"() {
        given:
        ClassPathAnnotationScanner scanner = new ClassPathAnnotationScanner()

        when:
        def classes = scanner.scan(AutoClone, getClass().getPackage())
        def results = classes.collect(Collectors.toList())

        then:
        results.contains(Foo)
        results.contains(Foo2)
        results.size() == 2
    }

    void "test scan multiple packages"() {
        given:
        ClassPathAnnotationScanner scanner = new ClassPathAnnotationScanner()

        when:
        def classes = scanner.scan(AutoClone, getClass().getPackage().getName(), "io.micronaut.ast.groovy.scan2")
        def results = classes.collect(Collectors.toList())

        then:
        results.contains(Foo)
        results.contains(Foo2)
        results.contains(Foo3)
        results.size() == 3
    }

    void "test scan classes from JAR file"() {
        given:
        ClassPathAnnotationScanner scanner = new ClassPathAnnotationScanner()

        when:
        def classes = scanner.scan(GroovyASTTransformation, "groovy.beans")

        then:
        classes.count() == 3
    }
}

@AutoClone
class Foo {
    String name
}


class Bar {

}