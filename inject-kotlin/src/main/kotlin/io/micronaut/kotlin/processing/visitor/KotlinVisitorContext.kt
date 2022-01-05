package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.value.MutableConvertibleValues
import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.ClassGenerationException
import io.micronaut.inject.writer.GeneratedFile
import io.micronaut.kotlin.processing.AnnotationUtils
import io.micronaut.kotlin.processing.KotlinAnnotationMetadataBuilder
import java.io.*
import java.net.URI
import java.nio.file.Files
import java.util.*
import java.util.function.BiConsumer

class KotlinVisitorContext(private val environment: SymbolProcessorEnvironment,
                           val resolver: Resolver) : VisitorContext {

    private val visitorAttributes: MutableConvertibleValues<Any>
    private val annotationUtil: AnnotationUtils
    private val elementFactory: KotlinElementFactory
    private val serviceDescriptors: LinkedHashMap<String, MutableSet<String>> = LinkedHashMap()


    init {
        visitorAttributes = MutableConvertibleValuesMap()
        annotationUtil = AnnotationUtils(environment, resolver)
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

    override fun getClassElement(name: String): Optional<ClassElement> {
        var declaration = resolver.getClassDeclarationByName(name)
        if (declaration == null) {
            declaration = resolver.getClassDeclarationByName(name.replace('$', '.'))
        }
        return Optional.ofNullable(declaration?.asStarProjectedType())
            .map(elementFactory::newClassElement)
    }

    @OptIn(KspExperimental::class)
    override fun getClassElements(aPackage: String, vararg stereotypes: String): Array<ClassElement> {
        return resolver.getDeclarationsFromPackage(aPackage)
            .filterIsInstance<KSClassDeclaration>()
            .filter { declaration ->
                declaration.annotations.any { ann ->
                    stereotypes.contains(KotlinAnnotationMetadataBuilder.getAnnotationTypeName(ann))
                }
            }
            .map { declaration ->
                elementFactory.newClassElement(declaration.asStarProjectedType())
            }
            .toList()
            .toTypedArray()
    }

    fun getAnnotationUtils() = annotationUtil

    override fun getServiceEntries(): MutableMap<String, MutableSet<String>> {
        return serviceDescriptors
    }

    override fun visitClass(classname: String, vararg originatingElements: Element): OutputStream {
        return environment.codeGenerator.createNewFile(
            getNativeElements(originatingElements),
            classname.substringBeforeLast('.'),
            classname.substringAfterLast('.'),
            "class")
    }

    override fun visitServiceDescriptor(type: String, classname: String) {
        if (StringUtils.isNotEmpty(type) && StringUtils.isNotEmpty(classname)) {
            serviceDescriptors.computeIfAbsent(type) { s -> LinkedHashSet() }.add(classname)
        }
    }

    override fun visitMetaInfFile(path: String, vararg originatingElements: Element): Optional<GeneratedFile> {
        val elements = path.split(File.separator).toMutableList()
        elements.add(0, "META-INF")
        val file = elements.removeAt(elements.size - 1)

        val stream = environment.codeGenerator.createNewFile(
            getNativeElements(originatingElements),
            elements.joinToString("."),
            file.substringBeforeLast('.'),
            file.substringAfterLast('.'))

        return Optional.of(KspGeneratedFile(stream, elements.joinToString(File.separator)))
    }

    override fun visitGeneratedFile(path: String?): Optional<GeneratedFile> {
        TODO("Not yet implemented")
    }

    override fun finish() {
        for ((serviceName, value) in serviceEntries) {
            val serviceTypes: MutableSet<String> = TreeSet(value)
            val serviceFile = visitMetaInfFile("services/$serviceName",  *Element.EMPTY_ELEMENT_ARRAY)
            if (serviceFile.isPresent) {
                val generatedFile = serviceFile.get()

                // add the existing definitions
                try {
                    BufferedReader(generatedFile.openReader()).use { bufferedReader ->
                        var line = bufferedReader.readLine()
                        while (line != null) {
                            serviceTypes.add(line)
                            line = bufferedReader.readLine()
                        }
                    }
                } catch (x: FileNotFoundException) {
                    // doesn't exist
                } catch (e: Throwable) {
                    throw ClassGenerationException("Failed to load existing service definition files: $e", e)
                }

                // write out new definitions
                try {
                    BufferedWriter(generatedFile.openWriter()).use { writer ->
                        for (serviceType in serviceTypes) {
                            writer.write(serviceType)
                            writer.newLine()
                        }
                    }
                } catch (x: IOException) {
                    throw ClassGenerationException("Failed to open writer for service definition files: $x")
                }
            }
        }

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
            if (element is AbstractKotlinElement<*>) {
                val el = element.nativeType
                logger.accept(message, el)
            } else {
                logger.accept(message, null)
            }
        }
    }

    private fun getNativeElements(originatingElements: Array<out Element>): Dependencies {
        val originatingFiles: MutableList<KSFile> = ArrayList(originatingElements.size)
        for (originatingElement in originatingElements) {
            val nativeType = originatingElement.nativeType
            if (nativeType is KSFile) {
                originatingFiles.add(nativeType)
            }
        }
        return Dependencies(false, *originatingFiles.toTypedArray())
    }

    class KspGeneratedFile(private val outputStream: OutputStream,
                           private val path: String) : GeneratedFile {

        private val file = File(path)

        override fun toURI(): URI {
            return file.toURI()
        }

        override fun getName(): String {
            return file.name
        }

        override fun openInputStream(): InputStream {
            return Files.newInputStream(file.toPath())
        }

        override fun openOutputStream(): OutputStream = outputStream

        override fun openReader(): Reader {
            return file.reader()
        }

        override fun getTextContent(): CharSequence {
            return file.readText()
        }

        override fun openWriter(): Writer {
            return OutputStreamWriter(outputStream)
        }

    }
}
