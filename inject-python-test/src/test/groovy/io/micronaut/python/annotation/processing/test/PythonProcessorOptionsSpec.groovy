package io.micronaut.python.annotation.processing.test

import io.micronaut.python.processing.PythonAnnotationProcessor
import spock.lang.Specification

/**
 * The options the Python processing reads have to be advertised by the processor, not only
 * understood by whichever visitor reads them.
 *
 * <p>A build tool that lets an application configure an option -- Pyronaut forwards these from an
 * application's own {@code application.toml} -- discovers which options exist by asking each
 * registered {@code javax.annotation.processing.Processor} for its supported options. An option
 * declared by a {@code TypeElementVisitor} is invisible to that enumeration, so an application
 * could set it only by passing {@code -A} to {@code javac} by hand.
 */
class PythonProcessorOptionsSpec extends Specification {

    void "the processor advertises the options its visitors read"() {
        given: "the names as a tool and an application see them, which is why they are literals here"
        def expected = [
            "micronaut.python.source.root",
            "micronaut.introspection.allowReflection",
            "micronaut.python.reflection.warnings",
            "micronaut.python.pooled.ignoreDependencies"
        ]

        when:
        def supported = new PythonAnnotationProcessor().getSupportedOptions()

        then:
        expected.every { supported.contains(it) }
    }
}
