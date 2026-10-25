package io.micronaut.python.annotation.processing.test

import io.micronaut.context.python.PythonStatic
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode

/**
 * The compiled bodies whose Python callers run the Java body too, beyond the simplest ones: each
 * feature compiles its source in modes {@code off} and {@code all}, asserts the delegation the
 * decision reports, that a Python caller ran the Java body in mode {@code all}, and that the
 * Python caller saw the same results and exceptions in both modes.
 */
class StaticCompilationDelegationCoverageSpec extends AbstractPythonTypeElementSpec {

    List<StaticCompilationDecision> decisions = []
    StaticCompilationMode mode = StaticCompilationMode.ALL

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(mode)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    /**
     * Compiles the source in both modes and calls {@code Caller.probe()}, a Python method, in each.
     *
     * @param source    The source, with a {@code Caller} bean whose {@code probe()} returns a string
     * @param delegated The compiled bodies, by qualified name, with the delegation each must report
     * @return The results of the probe in mode {@code off} and in mode {@code all}, and the Java body entries of mode all
     */
    Map<String, Object> parity(String source, Map<String, String> delegated) {
        Map<String, Object> results = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            PythonStatic.resetEntries()
            def context = buildContext(source)
            try {
                results[m.optionValue()] = getBean(context, 'python.Caller').probe()
                if (m == StaticCompilationMode.ALL) {
                    delegated.each { name, status ->
                        def decision = decisions.find { it.qualifiedName() == name }
                        assert decision?.outcome() == StaticCompilationDecision.Outcome.COMPILED: "$name: $decision"
                        assert decision.delegation() == status
                        // Class.method counts as python.Class#method; a module function names its key itself
                        String key = name.contains('#') ? name : 'python.' + name.replaceFirst('\\.', '#')
                        results['entries.' + name] = PythonStatic.entries(key)
                    }
                }
            } finally {
                context.close()
            }
        }
        return results
    }

    void "a body reading plain and fixed attributes of self delegates in a compilation with exception classes"() {
        when:
        def results = parity('''
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Value
from micronaut.context.python.annotation import CompileStatic
from java.lang import Integer

class InvalidCode(Exception):
    pass

@Singleton
class Codes:
    suffix: str

    def __init__(self, base: Annotated[str, Value("${codes.base:1}")], suffix: Annotated[str, Value("${codes.suffix:0}")]):
        self.base = base
        self.suffix = suffix

    def parse(self, text: str) -> str:
        return str(Integer.parseInt(self.base + text + self.suffix))

    @CompileStatic(False)
    def change(self, suffix: str):
        self.suffix = suffix

@Singleton
class Guarded:
    @property
    def base(self) -> str:
        return "4"

    def parse(self, text: str) -> str:
        # the getter is Python code of the compilation, which may raise InvalidCode
        return str(Integer.parseInt(self.base + text))

@Singleton
@CompileStatic(False)
class Caller:
    def __init__(self, codes: Codes, guarded: Guarded):
        self.codes = codes
        self.guarded = guarded

    def probe(self) -> str:
        first = self.codes.parse("2")
        self.codes.change("5")
        second = self.codes.parse("3")
        try:
            self.codes.parse("x")
            failed = "none"
        except Exception as e:
            failed = "NumberFormatException" in type(e).__name__ or "NumberFormatException" in str(e)
        return f"{first} {second} {failed} {self.guarded.parse('2')}"
''', ['Codes.parse': 'once', 'Guarded.parse': 'none(a Java exception of a Python exception class may come from the compilation or from Java code)'])

        then:
        results.off == '120 135 True 42'
        results.all == results.off
        results['entries.Codes.parse'] == 3
        results['entries.Guarded.parse'] == 0
    }
}
