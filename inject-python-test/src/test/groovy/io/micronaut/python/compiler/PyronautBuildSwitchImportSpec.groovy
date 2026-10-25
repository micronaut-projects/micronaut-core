package io.micronaut.python.compiler

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.ValueCoercible
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * The {@code CompileStatic} and {@code TypeChecked} switches imported from {@code pyronaut.build}
 * are decorators the application still runs with: the import binds them at run time too.
 */
class PyronautBuildSwitchImportSpec extends AbstractPythonTypeElementSpec {

    void "the switches imported from pyronaut.build, plain or aliased, run as decorators"() {
        given:
        ApplicationContext context = buildContext('''
from jakarta.inject import Singleton
from pyronaut.build import CompileStatic
from pyronaut.build import CompileStatic as CS, TypeChecked
from pyronaut.build import TypeChecked as Checked


@Singleton
class Aliases:
    @CompileStatic
    def plain(self, n: int) -> int:
        return n + 1

    @CS(False)
    def aliased(self, n: int) -> int:
        return n + 2

    @TypeChecked
    @Checked(False)
    def checked(self, n: int) -> int:
        return n + 3

    @CompileStatic
    @property
    def outer(self) -> int:
        return 6


@CS
@Singleton
class AliasedClass:
    def run(self, n: int) -> int:
        return n + 4
''')

        when:
        def aliases = getBean(context, 'python.Aliases')
        def aliasedClass = getBean(context, 'python.AliasedClass')

        then:
        aliases.plain(1) == 2
        aliases.aliased(1) == 3
        aliases.checked(1) == 4
        aliasedClass.run(1) == 5
        // a bare switch above @property decorates the property object, which it returns as it is
        ((ValueCoercible) aliases).asPolyglotValue().getMember("outer").asInt() == 6

        cleanup:
        context?.close()
    }
}
