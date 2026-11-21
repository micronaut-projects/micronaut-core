package io.micronaut.kotlin.processing.expressions

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.context.annotation.Value
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import spock.lang.Specification

/**
 * An element declared outside of a class, such as a top-level function or one of its parameters, has no class to
 * name the class of an evaluated expression after: the expression stays the value written, with a warning.
 */
class TopLevelExpressionSpec extends Specification {

    void "an evaluated expression on a top-level function or its parameter stays a plain value with a warning"() {
        given:
        Map<String, Object> values = [:]
        SymbolProcessorProvider provider = new SymbolProcessorProvider() {
            @Override
            SymbolProcessor create(SymbolProcessorEnvironment environment) {
                return new SymbolProcessor() {
                    boolean done

                    @Override
                    List<KSAnnotated> process(Resolver resolver) {
                        if (!done) {
                            done = true
                            def builder = new KotlinVisitorContext(environment, resolver).annotationMetadataBuilder
                            def function = (KSFunctionDeclaration) resolver.getFunctionDeclarationsByName(resolver.getKSNameFromString('test.top'), true).iterator().next()
                            values.function = builder.buildDeclared(function).getValues(Value.name).get("value")
                            values.parameter = builder.buildDeclared(function.parameters.first()).getValues(Value.name).get("value")
                        }
                        return []
                    }
                }
            }
        }

        when:
        def result = KotlinCompiler.compile('test.Top', '''
            package test

            import io.micronaut.context.annotation.Value

            @Value("#{ 1 + 1 }")
            fun top(@Value("#{ 2 + 2 }") a: String) {
            }
        ''', { }, [provider]).first.second

        then: "the values are those written, not references to expression classes that are never generated"
        values.function == '#{ 1 + 1 }'
        values.parameter == '#{ 2 + 2 }'

        and: "the compilation says so"
        result.messages.count("The evaluated expression of the member 'value' of the annotation @${Value.name} is not evaluated") == 2
    }
}
