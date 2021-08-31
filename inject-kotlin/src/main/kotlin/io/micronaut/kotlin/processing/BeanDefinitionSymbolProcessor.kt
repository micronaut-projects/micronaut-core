package io.micronaut.kotlin.processing;

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

class BeanDefinitionSymbolProcessor(private val environment: SymbolProcessorEnvironment): SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {

    }

    override fun finish() {

    }

    override fun onError() {

    }
}
