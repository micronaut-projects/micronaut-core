package io.micronaut.python.annotation.processing.test

class ModuleScriptSpec extends AbstractPythonTypeElementSpec {

    void "module MicronautTest metadata is emitted on generated class and test methods"() {
        given:
        def python = '''
def micronaut_annotation(name):
    def decorator(func):
        return func
    return decorator

@micronaut_annotation("io.micronaut.test.extensions.junit5.annotation.MicronautTest")
def MicronautTest(func=None):
    return func

MicronautTest()

def helper(self):
    return "helper"

def test_root(self):
    pass
'''

        when:
        def context = buildContext(python, true)
        def generated = context.classLoader.loadClass("python.Script")

        then:
        generated.getAnnotation(context.classLoader.loadClass("io.micronaut.test.extensions.junit5.annotation.MicronautTest"))
        generated.getDeclaredMethod("test_root").getAnnotation(org.junit.jupiter.api.Test)
        !generated.declaredMethods*.name.contains("decorator")

        and:
        !generated.interfaces*.name.contains("io.micronaut.context.python.PooledValueCoercible")

        cleanup:
        context?.close()
    }
}
