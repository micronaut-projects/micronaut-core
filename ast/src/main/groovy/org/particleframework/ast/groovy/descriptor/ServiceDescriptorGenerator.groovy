package org.particleframework.ast.groovy.descriptor

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.io.FileReaderSource
import org.codehaus.groovy.control.io.ReaderSource
import org.codehaus.groovy.control.io.URLReaderSource
import org.particleframework.ast.groovy.utils.AstMessageUtils

/**
 * Helper class for generating service descriptors stored in META-INF/services
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ServiceDescriptorGenerator {

    /**
     * Generates a service descriptor
     *
     * @param classNode The class node the service descriptor is for
     * @param serviceType The service type
     */
    void generate(ClassNode classNode, Class serviceType) {
        SourceUnit sourceUnit = classNode.module?.context
        if(sourceUnit != null) {
            ReaderSource readerSource = sourceUnit.getSource()
            // Don't generate for runtime compiled scripts
            if (readerSource instanceof FileReaderSource || readerSource instanceof URLReaderSource) {

                File targetDirectory = sourceUnit.configuration.targetDirectory
                if (targetDirectory == null) {
                    targetDirectory = new File("build/resources/main")
                }

                File servicesDir = new File(targetDirectory, "META-INF/services")
                servicesDir.mkdirs()

                String className = classNode.name
                try {
                    def descriptor = new File(servicesDir, serviceType.name)
                    if (descriptor.exists()) {
                        String ls = System.getProperty('line.separator')
                        String contents = descriptor.text
                        def entries = contents.split('\\n')
                        if (!entries.contains(className)) {
                            descriptor.append("${ls}${className}")
                        }
                    } else {
                        descriptor.text = className
                    }
                } catch (Throwable e) {
                    AstMessageUtils.warning(sourceUnit, classNode, "Error generating service loader descriptor for class [${className}]: $e.message")
                }
            }
        }
    }
}
