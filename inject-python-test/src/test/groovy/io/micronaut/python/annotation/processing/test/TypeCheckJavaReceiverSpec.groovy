package io.micronaut.python.annotation.processing.test

import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.diagnostic.PythonDiagnostic
import io.micronaut.python.processing.typecheck.TypeCheckMode
import spock.lang.Specification

class TypeCheckJavaReceiverSpec extends Specification {

    private static List<PythonDiagnostic> check(String source, TypeCheckMode mode = TypeCheckMode.ERROR) {
        List<PythonDiagnostic> diagnostics = []
        try {
            PyronautCompiler.builder()
                .pythonCode(source)
                .typeCheck(mode)
                .pythonDiagnosticCallback { diagnostics.add(it) }
                .build()
                .buildClassLoader()
        } catch (RuntimeException ignored) {
            // the diagnostics of a failing compilation are what the tests read
        }
        return diagnostics
    }

    void "a method that does not exist on a Java receiver is reported with the name it most likely meant"() {
        when:
        def diagnostics = check('''
from jakarta.inject import Singleton
from java.util import ArrayList

@Singleton
class Service:
    def run(self, name: str) -> int:
        values = ArrayList()
        values.ad(name)
        values.add_all(values)
        return values.size()
''')

        then:
        diagnostics*.rule() == ['unknown-method', 'unknown-method']
        diagnostics[0].message() == 'Java type [java.util.ArrayList] has no method named [ad]; did you mean [add]?'
        diagnostics[0].span().line() == 9
        diagnostics[0].span().column() == 9
        diagnostics[1].message() == 'Java type [java.util.ArrayList] has no method named [add_all]; did you mean [addAll]?'
    }

    void "an overload must accept the arguments the checker can type"() {
        when:
        def diagnostics = check('''
from jakarta.inject import Singleton
from java.util import ArrayList

@Singleton
class Service:
    def run(self) -> str:
        values = ArrayList()
        values.add("a", "b", "c")
        values.add("a")
        values.add(0, "b")
        values.add(True, 1, 2)
        return "x".join(values)
''')

        then:
        diagnostics*.rule() == ['no-matching-overload', 'no-matching-overload']
        diagnostics[0].message().startsWith('no overload of [ArrayList.add] accepts (str, str, str); candidates: ')
        diagnostics[1].message().startsWith('no overload of [ArrayList.add] accepts (bool, int, int); candidates: ')
    }

    void "attribute reads on Java receivers must name a field, a nested type, an enum constant or a method"() {
        when:
        def diagnostics = check('''
from jakarta.inject import Singleton
from micronaut.http import HttpStatus
from java.util import Locale
from java.lang import Integer

@Singleton
class Service:
    def run(self) -> str:
        status = HttpStatus.CREATE
        fine = HttpStatus.CREATED
        maximum = Integer.MAX_VALUE
        wrong = Integer.MAX_VAL
        language = Locale.ROOT.language
        return fine.getReason() + language
''')

        then:
        diagnostics*.rule() == ['unknown-attribute', 'unknown-attribute', 'unknown-attribute']
        diagnostics[0].message() == 'Java type [io.micronaut.http.HttpStatus] has no member named [CREATE]; did you mean [CREATED]?'
        diagnostics[1].message() == 'Java type [java.lang.Integer] has no member named [MAX_VAL]; did you mean [MAX_VALUE]?'
        diagnostics[2].message() == 'Java type [java.util.Locale] has no member named [language]; did you mean [getLanguage()]?'
    }

    void "constructors are checked and abstract types cannot be instantiated"() {
        when:
        def diagnostics = check('''
from jakarta.inject import Singleton
from java.util import ArrayList, List
from java.io import InputStream
from java.lang import StringBuilder

@Singleton
class Service:
    def run(self) -> str:
        fine = StringBuilder("x")
        sized = StringBuilder(16)
        wrong = StringBuilder(True)
        values = List()
        stream = InputStream()
        return fine.toString()
''')

        then:
        diagnostics*.rule() == ['unknown-constructor', 'abstract-instantiation', 'abstract-instantiation']
        diagnostics[0].message().startsWith('no constructor of [java.lang.StringBuilder] accepts (bool); candidates: ')
        diagnostics[1].message() == '[java.util.List] is an interface and cannot be instantiated'
        diagnostics[2].message() == '[java.io.InputStream] is abstract and cannot be instantiated'
    }

    void "the checker follows values through locals, parameters, fields and return types"() {
        when:
        def diagnostics = check('''
from jakarta.inject import Singleton
from java.time import LocalDate
from java.util import Locale, StringJoiner

@Singleton
class Service:
    def run(self, text: StringJoiner, when: LocalDate, tag: str) -> str:
        longer = text.add("x")
        longer.addd("y")
        when.plusWeek(1)
        when.isoformat()
        display = Locale.ROOT.getDisplayName()
        display.trimmed()
        tag.upper()
        return display
''')

        then: "a Java String is a Python str and a LocalDate a datetime at run time, so their methods are never judged"
        diagnostics*.rule() == ['unknown-method']
        diagnostics[0].message() == 'Java type [java.util.StringJoiner] has no method named [addd]; did you mean [add]?'
    }

    void "class receivers offer static members, arrays take lists, and types without constructors are reported"() {
        when:
        def diagnostics = check('''
import java
from jakarta.inject import Singleton
from java.util import Arrays, ArrayList, AbstractMap
from java.lang import System, Integer
from micronaut.http import HttpStatus

@Singleton
class Service:
    def run(self) -> str:
        sorted_values = Arrays.asList("b", "a")
        Arrays.sort([3, 1, 2])
        Integer.parseInt("1")
        ArrayList.add("x")
        Integer.MAX_VALUE
        clock = System()
        status = HttpStatus()
        HttpStatus.values()
        HttpStatus.valueOf("OK").name()
        HttpStatus.OK.ordinal()
        HttpStatus.name()
        HttpStatus.valueOf("OK").getCod()
        callback = ArrayList.add
        AbstractMap.SimpleEntry("k", 1, 2)
        java.util.ArrayList(1, 2, 3)
        return System.lineSeparator()
''')

        then:
        diagnostics*.rule() == ['static-access', 'unknown-constructor', 'unknown-constructor', 'static-access', 'unknown-method', 'static-access', 'unknown-constructor', 'unknown-constructor']
        diagnostics[0].message() == '[ArrayList.add] is an instance method; call it on an instance of [java.util.ArrayList]'
        diagnostics[1].message() == '[java.lang.System] is a type without an accessible constructor and cannot be instantiated'
        diagnostics[2].message() == '[io.micronaut.http.HttpStatus] is an enum and cannot be instantiated'
        diagnostics[3].message().startsWith('[HttpStatus.name] is an instance method; call it on a')
        diagnostics[4].message() == 'Java type [io.micronaut.http.HttpStatus] has no method named [getCod]; did you mean [getCode]?'
        diagnostics[5].message() == '[ArrayList.add] is an instance method; read it on an instance of [java.util.ArrayList]'
        diagnostics[6].message().startsWith('no constructor of [java.util.AbstractMap$SimpleEntry] accepts (str, int, int)')
        diagnostics[7].message().startsWith('no constructor of [java.util.ArrayList] accepts (int, int, int)')
    }

    void "anything the checker cannot type is left alone"() {
        expect:
        check('''
from jakarta.inject import Singleton
from java.util import ArrayList, HashMap, List
import requests

@Singleton
class Service:
    def run(self, payload, values: ArrayList, parameters: HashMap, registry: List) -> str:
        payload.whatever()
        registry.whatever_the_implementation_offers()
        registry.add("x")
        response = requests.get("http://example.com")
        response.no_such_thing()
        values = payload.something()
        values.nothing_known_here()
        for item in [1, 2]:
            item.anything()
        for name, value in parameters.items():
            value.setValue(1)
        parameters.get("x").setValue(2)
        parameters.keys()
        return "x"
''').isEmpty()
    }
}
