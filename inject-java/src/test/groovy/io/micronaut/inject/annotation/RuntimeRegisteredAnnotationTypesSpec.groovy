package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptorBindingDefinitions
import io.micronaut.aop.bytebuddy.ByteBuddyStacktraceVerified
import io.micronaut.core.annotation.AnnotationClassValue

import javax.tools.JavaFileObject
import java.nio.charset.StandardCharsets

class RuntimeRegisteredAnnotationTypesSpec extends AbstractTypeElementSpec {

    void "the classes generated for a bean do not depend on the annotation types code that ran in the JVM registered"() {
        given: 'generated code that ran registered the annotation types, as an application in the same JVM as an embedded compiler does'
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(InterceptorBindingDefinitions))
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(ByteBuddyStacktraceVerified))

        when:
        Map<String, byte[]> classes = classes('''
package test;

import io.micronaut.aop.bytebuddy.ByteBuddyStacktraceVerified;
import jakarta.inject.Singleton;

@Singleton
class Service {
    @ByteBuddyStacktraceVerified
    public String work() {
        return "work";
    }
}
''')
        String executable = new String(classes.find { it.key.endsWith('$Service$Definition$Exec.class') }.value, StandardCharsets.ISO_8859_1)

        then: 'the generated class registers the types itself, as it does in a JVM that never ran it'
        executable.contains('registerAnnotationType')
    }

    private Map<String, byte[]> classes(String source) {
        Map<String, byte[]> classes = [:]
        try (def parser = newJavaParser()) {
            for (JavaFileObject file : parser.generate('test.Service', source)) {
                if (file.kind == JavaFileObject.Kind.CLASS) {
                    classes[file.name] = file.openInputStream().bytes
                }
            }
        }
        return classes
    }
}
