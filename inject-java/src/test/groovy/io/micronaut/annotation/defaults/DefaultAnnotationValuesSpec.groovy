package io.micronaut.annotation.defaults


import io.micronaut.context.ApplicationContext
import io.micronaut.inject.annotation.AnnotationMetadataSupport
import spock.lang.Specification

class DefaultAnnotationValuesSpec extends Specification {

    void "test which default values are preserved"() {
        given:
            ApplicationContext ctx = ApplicationContext.run()

        when:
            def am = ctx.getBeanDefinition(MyBean).getAnnotationMetadata()
            ctx.getBean(MyBean) != null
        then:
            noExceptionThrown()

        when:
            def defaults1 = AnnotationMetadataSupport.getDefaultValues("io.micronaut.annotation.defaults.DefaultValues1")
            def defaults1Annotation = am.getAnnotation("io.micronaut.annotation.defaults.DefaultValues1")

        then: "empty string default value is emitted"
            am.stringValue("io.micronaut.annotation.defaults.DefaultValues1").isEmpty()
            defaults1Annotation.stringValue().isEmpty()
            defaults1Annotation.getRequiredValue("strings", String[].class).size() == 0
            defaults1.size() == 2
            defaults1["strings"].length == 0
            defaults1["ints"].length == 0
            am.stringValues("io.micronaut.annotation.defaults.DefaultValues1", "strings").size() == 0

        when:
            defaults1Annotation.getRequiredValue(String.class)
        then: "Exception even so the default is provided"
            thrown(IllegalStateException)

        when:
            def defaults2 = AnnotationMetadataSupport.getDefaultValues("io.micronaut.annotation.defaults.DefaultValues2")
            def defaults2Annotation = am.getAnnotation("io.micronaut.annotation.defaults.DefaultValues2")

        then:
            am.stringValue("io.micronaut.annotation.defaults.DefaultValues2").isEmpty()
            defaults2Annotation.stringValue().isEmpty()
            defaults2Annotation.getRequiredValue(String.class) == "xyz"
            defaults2.size() == 3
            defaults2["value"] == "xyz"
            defaults2["strings"].length == 0
            defaults2["ints"].length == 0

        when:
            def defaults3 = AnnotationMetadataSupport.getDefaultValues("io.micronaut.annotation.defaults.DefaultValues3")
            def defaults3Annotation = am.getAnnotation("io.micronaut.annotation.defaults.DefaultValues3")
        then:
            am.stringValue("io.micronaut.annotation.defaults.DefaultValues3").isEmpty()
            defaults3Annotation.stringValue().isEmpty()
            defaults3Annotation.stringValues("strings").size() == 0
            defaults3Annotation.getRequiredValue("strings", String[].class).size() == 1
            defaults3Annotation.getRequiredValue("strings", String[].class)[0] == ""
            defaults3.size() == 2
            defaults3["strings"].length == 1
            defaults3["strings"][0] == ""
            defaults2["ints"].length == 0

        cleanup:
            ctx.close()
    }

}
