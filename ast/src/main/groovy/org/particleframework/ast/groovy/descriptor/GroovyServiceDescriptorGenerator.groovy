package org.particleframework.ast.groovy.descriptor

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.io.FileReaderSource
import org.codehaus.groovy.control.io.ReaderSource
import org.codehaus.groovy.control.io.URLReaderSource
import org.particleframework.ast.groovy.utils.AstMessageUtils
import org.particleframework.core.io.service.ServiceDescriptorGenerator

/**
 * Helper class for generating service descriptors stored in META-INF/services
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class GroovyServiceDescriptorGenerator {

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
                if (targetDirectory != null) {
                    String className = classNode.name
                    try {
                        new ServiceDescriptorGenerator().generate(targetDirectory, className, serviceType)
                    } catch (Throwable e) {
                        AstMessageUtils.warning(sourceUnit, classNode, "Error generating service loader descriptor for class [${className}]: $e.message")
                    }
                }
            }
        }
    }



}
