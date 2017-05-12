package org.particleframework.ast.groovy.annotation

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Scope
import javax.inject.Singleton

/**
 * Created by graemerocher on 11/05/2017.
 */
class AnnotationStereoTypeFinderSpec extends Specification {

    void "test has stereotype"() {
        given:
        AnnotationStereoTypeFinder finder = new AnnotationStereoTypeFinder()
        ClassNode cn = ClassHelper.make(Foo)

        expect:
        finder.hasStereoType(cn, Scope)
        finder.hasStereoType(cn, Singleton)
        !finder.hasStereoType(cn, Inject)
    }
}

@Singleton
class Foo {}