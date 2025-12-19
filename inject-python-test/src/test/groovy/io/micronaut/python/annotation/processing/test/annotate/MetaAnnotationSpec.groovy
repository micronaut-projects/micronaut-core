package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class MetaAnnotationSpec extends AbstractPythonTypeElementSpec {

    void "test meta-annotation values are readable via stereotype"() {
        given:
        // Use the Java annotations defined in this package:
        // @InnerAnn is annotated with @OuterAnn(value="stereo", number=99)
        def definition = buildBeanDefinition('python', 'Test', '''
from jakarta.inject import Singleton
import java

InnerAnn = java.type("io.micronaut.python.annotation.processing.test.annotate.InnerAnn")

@InnerAnn()
@Singleton
class Test:
    pass
''')

        when:
        AnnotationMetadata metadata = definition.getAnnotationMetadata()

        then: "Declared annotation is present"
        metadata.hasDeclaredAnnotation(InnerAnn)

        and: "Meta-annotation stereotype is present, and its values are readable"
        metadata.hasStereotype(OuterAnn)
        metadata.stringValue(OuterAnn, "value").get() == "stereo"
        metadata.getValue(OuterAnn, "number", Integer).get() == 99

        and: "Lookup by name also works"
        def outerName = OuterAnn.name
        metadata.hasStereotype(outerName)
        metadata.stringValue(outerName, "value").get() == "stereo"
        metadata.getValue(outerName, "number", Integer).get() == 99
    }
}
