package io.micronaut.kotlin.processing.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.visitor.TypeElementVisitor

/**
 * The classes looked up anew in {@link TypeElementVisitor#finishCompilation(io.micronaut.inject.visitor.VisitorContext)}
 * resolve their overridden methods anew, though the last round looked them up already.
 */
class FinishCompilationSymbolsSpec extends AbstractKotlinCompilerSpec {

    void setup() {
        OverridesVisitor.OVERRIDDEN.clear()
    }

    void "test the overridden methods looked up in the last round are looked up anew once the compilation is finished"() {
        when:
        buildContext('''
package overrides

interface Greeter {
    fun greet(name: String): String
}

class Impl : Greeter {
    override fun greet(name: String): String = "Hello " + name
}
''').close()

        then:
        OverridesVisitor.OVERRIDDEN.toSorted() == [
                'overrides.Greeter.greet -> []',
                'overrides.Impl.greet -> [overrides.Greeter.greet:java.lang.String]'
        ]
    }
}
