package io.micronaut.kotlin.processing

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSFile
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.ast.Element
import io.micronaut.inject.writer.ClassGenerationException
import io.micronaut.inject.writer.ClassWriterOutputVisitor
import io.micronaut.inject.writer.GeneratedFile
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext
import java.io.*
import java.util.*

class KotlinOutputVisitor(private val environment: SymbolProcessorEnvironment): ClassWriterOutputVisitor {

    private val serviceDescriptors: LinkedHashMap<String, MutableSet<String>> = LinkedHashMap()

    override fun visitClass(classname: String, vararg originatingElements: Element): OutputStream {
        return environment.codeGenerator.createNewFile(
            getNativeElements(originatingElements),
            classname.substringBeforeLast('.'),
            classname.substringAfterLast('.'),
            "class")
    }

    override fun getServiceEntries(): MutableMap<String, MutableSet<String>> = serviceDescriptors

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

        return Optional.of(KotlinVisitorContext.KspGeneratedFile(stream, elements.joinToString(File.separator)))
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
}
