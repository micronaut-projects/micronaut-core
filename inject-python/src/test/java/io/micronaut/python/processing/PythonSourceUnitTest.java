package io.micronaut.python.processing;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the {@code unittest} modules under {@code python-tests/} inside the processor's GraalPy context, so
 * the compile-time Python sources have tests of their own.
 */
final class PythonSourceUnitTest {

    private static final List<String> MODULES = List.of("test_micronaut_processor", "test_micronaut_transformer");

    @Test
    void pythonUnitTestsPass() throws IOException {
        PythonAstParser parser = new PythonAstParser();
        try {
            Context context = parser.context();
            Value bindings = context.getBindings(PYTHON);
            for (String module : MODULES) {
                bindings.putMember("module_name", module);
                bindings.putMember("module_source", read("python-tests/" + module + ".py"));
                context.eval(PYTHON, """
                    import sys, types
                    _module = types.ModuleType(module_name)
                    exec(compile(module_source, module_name + ".py", "exec"), _module.__dict__)
                    sys.modules[module_name] = _module
                    """);
            }
            bindings.putMember("module_names", MODULES.toArray(String[]::new));
            Value result = context.eval(PYTHON, """
                import io, sys, unittest
                suite = unittest.TestSuite()
                for name in module_names:
                    suite.addTests(unittest.defaultTestLoader.loadTestsFromModule(sys.modules[name]))
                stream = io.StringIO()
                outcome = unittest.TextTestRunner(stream=stream, verbosity=2).run(suite)
                (outcome.testsRun, len(outcome.failures) + len(outcome.errors), stream.getvalue())
                """);
            int run = result.getArrayElement(0).asInt();
            int failed = result.getArrayElement(1).asInt();
            String report = result.getArrayElement(2).asString();
            assertTrue(run > 10, "expected the Python unit tests to run, got " + run + "\n" + report);
            assertEquals(0, failed, report);
        } finally {
            parser.close();
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = PythonSourceUnitTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
