package io.micronaut.python.annotation.processing.test.qualifiers

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * A remapper, mapper or transformer reading the metadata of an annotation's members while an occurrence
 * of the annotation is built re-enters the build of a member whose annotations name the annotation again.
 * The build of an element already in progress answers rather than starting over.
 *
 * @see MemberReadingAnnotationRemapper
 */
class MemberAnnotationRemapperReentrySpec extends AbstractPythonTypeElementSpec {

    void 'test a remapper reading the members of two annotations annotated with each other'() {
        given:
        def definition = buildBeanDefinition('python', 'Test', '''
from typing import Annotated
from jakarta.inject import Singleton

def micronaut_annotation(name, repeated=None, annotationTypeTarget=False):
    def decorator(func):
        return func
    return decorator

@micronaut_annotation("remapreentry.A")
def a_ann(v: Annotated[str, b_ann(v="on a")] = ""):
    def decorator(target):
        return target
    return decorator

@micronaut_annotation("remapreentry.B")
def b_ann(v: Annotated[str, a_ann(v="on b")] = ""):
    def decorator(target):
        return target
    return decorator

@Singleton
@a_ann(v="a")
class Test:
    pass
''')

        expect:
        definition.hasAnnotation('remapreentry.A')
        definition.getAnnotation('remapreentry.A').stringValue('v').get() == 'a'
    }
}
