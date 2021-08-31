package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSFile
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.value.MutableConvertibleValues
import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.ElementFactory
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.GeneratedFile
import io.micronaut.kotlin.processing.AnnotationUtils
import java.io.OutputStream
import java.util.*

class KotlinVisitorContext(private val environment: SymbolProcessorEnvironment) : VisitorContext {

    private val visitorAttributes: MutableConvertibleValues<Any>
    private val annotationUtil: AnnotationUtils

    init {
        visitorAttributes = MutableConvertibleValuesMap()
        annotationUtil = AnnotationUtils(environment)
    }

    override fun <T : Any?> get(name: CharSequence?, conversionContext: ArgumentConversionContext<T>?): Optional<T> {
        return visitorAttributes.get(name, conversionContext)
    }

    override fun names(): MutableSet<String> {
        return visitorAttributes.names()
    }

    override fun values(): MutableCollection<Any> {
        return visitorAttributes.values()
    }

    override fun put(key: CharSequence?, value: Any?): MutableConvertibleValues<Any> {
        visitorAttributes.put(key, value)
        return this
    }

    override fun remove(key: CharSequence?): MutableConvertibleValues<Any> {
        visitorAttributes.remove(key)
        return this
    }

    override fun clear(): MutableConvertibleValues<Any> {
        visitorAttributes.clear()
        return this
    }

    fun getAnnotationUtils() = annotationUtil

    override fun visitClass(classname: String, vararg originatingElements: Element): OutputStream {
        val originatingFiles: MutableList<KSFile> = ArrayList(originatingElements.size)
        for (originatingElement in originatingElements) {
            val nativeType = originatingElement.nativeType
            if (nativeType is KSFile) {
                originatingFiles.add(nativeType)
            }
        }
        val dependencies = Dependencies(false, *originatingFiles.toTypedArray())

        return environment.codeGenerator.createNewFile(
            dependencies,
            classname.substringBeforeLast('.'),
            classname.substringAfterLast('.'),
            "class")
    }

    override fun visitServiceDescriptor(type: String?, classname: String?) {
        TODO("Not yet implemented")
    }

    override fun visitMetaInfFile(path: String?, vararg originatingElements: Element?): Optional<GeneratedFile> {
        TODO("Not yet implemented")
    }

    override fun visitGeneratedFile(path: String?): Optional<GeneratedFile> {
        TODO("Not yet implemented")
    }

    override fun finish() {
        TODO("Not yet implemented")
    }

    override fun getElementFactory(): KotlinElementFactory {
        TODO("Not yet implemented")
    }

    override fun info(message: String?, element: Element?) {
        TODO("Not yet implemented")
    }

    override fun info(message: String?) {
        TODO("Not yet implemented")
    }

    override fun fail(message: String?, element: Element?) {
        TODO("Not yet implemented")
    }

    override fun warn(message: String?, element: Element?) {
        TODO("Not yet implemented")
    }
}
