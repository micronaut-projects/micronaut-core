package io.micronaut.annotation.processing

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.aop.introduction.Stub
import io.micronaut.http.annotation.Controller
import spock.lang.Specification

class TypeElementVisitorProcessorSpec extends Specification {

    void "test get annotation names"() {
        given:
        def visitedAnnotationNames = TypeElementVisitorProcessor.getVisitedAnnotationNames()

        visitedAnnotationNames.forEach(this::println)

        expect:
        visitedAnnotationNames
        visitedAnnotationNames.contains(Stub.name)
        visitedAnnotationNames.contains(Controller.name)
        !visitedAnnotationNames.contains("*")
    }
}
