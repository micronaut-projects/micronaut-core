package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.value.MutableConvertibleValues
import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.beans.BeanElement
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.GeneratedFile
import io.micronaut.kotlin.processing.AnnotationUtils
import java.io.OutputStream
import java.util.*
import java.util.function.BiConsumer
import javax.tools.Diagnostic

class KotlinVisitorContext(private val environment: SymbolProcessorEnvironment,
                           val resolver: Resolver) : VisitorContext {

    private val visitorAttributes: MutableConvertibleValues<Any>
    private val annotationUtil: AnnotationUtils
    private val elementFactory: KotlinElementFactory

    init {
        visitorAttributes = MutableConvertibleValuesMap()
        annotationUtil = AnnotationUtils(environment)
        elementFactory = KotlinElementFactory(this)
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

    override fun getElementFactory(): KotlinElementFactory = elementFactory

    override fun info(message: String, element: Element?) {
        printMessage(message, environment.logger::info, element)
    }

    override fun info(message: String) {
        printMessage(message, environment.logger::info, null)
    }

    override fun fail(message: String, element: Element?) {
        printMessage(message, environment.logger::error, element)
    }

    override fun warn(message: String, element: Element?) {
        printMessage(message, environment.logger::warn, element)
    }

    private fun printMessage(message: String, logger: BiConsumer<String, KSNode?>, element: Element?) {
        if (StringUtils.isNotEmpty(message)) {
            if (element is AbstractKotlinElement) {
                val el = element.nativeType
                logger.accept(message, el)
            } else {
                logger.accept(message, null)
            }
        }
    }
}
