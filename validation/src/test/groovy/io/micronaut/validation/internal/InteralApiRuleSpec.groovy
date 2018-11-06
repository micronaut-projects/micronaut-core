package io.micronaut.validation.internal

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class InteralApiRuleSpec extends AbstractTypeElementSpec {

    void "test missing parameter"() {
        given:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.out = new PrintStream(out)

        when:
        buildTypeElement("""

package test;

import io.micronaut.core.io.scan.*;

class Foo extends CachingClassPathAnnotationScanner {

}

""")
        String output = out.toString("UTF8")

        then:
        noExceptionThrown()
        output.contains("warning: Element extends or implements an internal or experimental API\n" +
                "class Foo extends CachingClassPathAnnotationScanner {")
        output.contains("Overriding an internal API may result in breaking changes in minor or patch versions")

        cleanup:
        System.out = oldOut
    }

}