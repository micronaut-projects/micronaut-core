package io.micronaut.dev.test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jupiter tests compiled into a directory and loaded through a loader of their own, as a generation's are.
 */
final class TestFixture implements AutoCloseable {

    static final String CALCULATOR = "fixture.CalculatorTest";
    static final String BROKEN = "fixture.BrokenSetupTest";
    static final String OTHER = "fixture.OtherTest";
    static final String FACTORY = "fixture.FactoryTest";

    private static final Map<String, String> SOURCES = Map.of(
        "fixture/CalculatorTest.java", """
            package fixture;

            import org.junit.jupiter.api.Assumptions;
            import org.junit.jupiter.api.Disabled;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;

            public class CalculatorTest {
                @Test
                void adds() {
                    System.out.println("adding <one> & one");
                    System.err.println("on stderr");
                    assertEquals(2, 1 + 1);
                }

                @Test
                void fails() {
                    assertEquals(3, 1 + 1, "wrong sum");
                }

                @Test
                void errors() {
                    throw new IllegalStateException("boom");
                }

                @Test
                @Disabled("not yet")
                void disabled() {
                }

                @Test
                void assumes() {
                    Assumptions.assumeTrue(false, "no network");
                }
            }
            """,
        "fixture/BrokenSetupTest.java", """
            package fixture;

            import org.junit.jupiter.api.BeforeAll;
            import org.junit.jupiter.api.Test;

            public class BrokenSetupTest {
                @BeforeAll
                static void setUp() {
                    throw new IllegalStateException("no database");
                }

                @Test
                void never() {
                }
            }
            """,
        "fixture/FactoryTest.java", """
            package fixture;

            import java.util.stream.Stream;
            import org.junit.jupiter.api.DynamicTest;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.TestFactory;

            public class FactoryTest {
                @TestFactory
                Stream<DynamicTest> generated() {
                    return Stream.of(DynamicTest.dynamicTest("one", () -> { }), DynamicTest.dynamicTest("two", () -> { }));
                }

                @Test
                void plain() {
                }
            }
            """,
        "fixture/EmptiedTest.java", """
            package fixture;

            public class EmptiedTest {
                void noLongerATest() {
                }
            }
            """,
        "fixture/OtherTest.java", """
            package fixture;

            import org.junit.jupiter.api.Test;

            public class OtherTest {
                @Test
                void other() {
                    System.out.println("loaded by " + getClass().getClassLoader().getName());
                }
            }
            """);

    final Path sources;
    final Path classes;
    final URLClassLoader loader;

    private TestFixture(Path sources, Path classes, URLClassLoader loader) {
        this.sources = sources;
        this.classes = classes;
        this.loader = loader;
    }

    static TestFixture compile(Path directory) throws IOException {
        Path sources = Files.createDirectories(directory.resolve("src/test/java"));
        Path classes = Files.createDirectories(directory.resolve("build/classes/test"));
        List<String> arguments = new ArrayList<>(List.of("-d", classes.toString(), "-cp", System.getProperty("java.class.path")));
        for (Map.Entry<String, String> source : SOURCES.entrySet()) {
            Path file = sources.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac.run(null, null, null, arguments.toArray(String[]::new)) != 0) {
            throw new IllegalStateException("The fixture does not compile");
        }
        URLClassLoader loader = new URLClassLoader("fixture-generation", new URL[] {classes.toUri().toURL()}, TestFixture.class.getClassLoader());
        return new TestFixture(sources, classes, loader);
    }

    TestRunRequest request(String runId, TestSelection selection) {
        return new TestRunRequest(runId, loader, List.of(classes), List.of(), selection, Map.of());
    }

    @Override
    public void close() throws IOException {
        loader.close();
    }
}
