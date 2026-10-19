package io.micronaut.dev;

import io.micronaut.dev.compile.CompilationRequest;
import io.micronaut.dev.compile.CompilationResult;
import io.micronaut.dev.compile.SourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JointCompilationTest {

    @TempDir
    Path project;

    @Test
    void aLanguageSharingTheOutputOfACompilerThatNamesItIsCompiledByThatCompilerOnly() throws Exception {
        DevManifest manifest = manifest("build/classes", "build/classes");
        RecordingCompiler python = new RecordingCompiler(SourceKind.PYTHON, Set.of(SourceKind.JAVA));
        RecordingCompiler javac = new RecordingCompiler(SourceKind.JAVA, Set.of());
        Map<SourceKind, SourceCompiler> compilers = compilers(python, javac);

        assertEquals(Map.of(SourceKind.JAVA, SourceKind.PYTHON), DevRuntime.jointOwners(manifest, compilers));

        DevRuntime.compileMissingOutputs(manifest, compilers);

        assertTrue(javac.requests.isEmpty());
        assertEquals(1, python.requests.size());
        CompilationRequest request = python.requests.getFirst();
        assertEquals(SourceKind.PYTHON, request.kind());
        assertTrue(request.isFull());
        assertEquals(List.of(new SourceRoot(SourceKind.PYTHON, project.resolve("src/main/python")), new SourceRoot(SourceKind.JAVA, project.resolve("src/main/java"))),
            request.sourceRoots());
        // one compiler run compiles both: the Java options come too, whole
        assertEquals(List.of("-Apython.option=yes", "-source", "21", "-target", "21", "-parameters"), request.options());
    }

    @Test
    void aLanguageWithItsOwnOutputIsCompiledByItsOwnCompiler() throws Exception {
        DevManifest manifest = manifest("build/classes/python", "build/classes/java");
        RecordingCompiler python = new RecordingCompiler(SourceKind.PYTHON, Set.of(SourceKind.JAVA));
        RecordingCompiler javac = new RecordingCompiler(SourceKind.JAVA, Set.of());
        Map<SourceKind, SourceCompiler> compilers = compilers(python, javac);

        assertTrue(DevRuntime.jointOwners(manifest, compilers).isEmpty());

        DevRuntime.compileMissingOutputs(manifest, compilers);

        assertEquals(1, javac.requests.size());
        assertEquals(List.of(new SourceRoot(SourceKind.JAVA, project.resolve("src/main/java"))), javac.requests.getFirst().sourceRoots());
        assertEquals(List.of("-source", "21", "-target", "21", "-parameters"), javac.requests.getFirst().options());
        assertEquals(List.of("-Apython.option=yes"), python.requests.getFirst().options());
        assertEquals(1, python.requests.size());
        assertEquals(List.of(new SourceRoot(SourceKind.PYTHON, project.resolve("src/main/python"))), python.requests.getFirst().sourceRoots());
    }

    private DevManifest manifest(String pythonOutput, String javaOutput) throws Exception {
        Files.createDirectories(project.resolve("src/main/python"));
        Files.createDirectories(project.resolve("src/main/java"));
        Properties properties = new Properties();
        properties.setProperty("micronaut.dev.main-class", "app.Application");
        properties.setProperty("micronaut.dev.reloadable", pythonOutput + "," + javaOutput);
        properties.setProperty("micronaut.dev.sources.python", "src/main/python");
        properties.setProperty("micronaut.dev.sources.java", "src/main/java");
        properties.setProperty("micronaut.dev.compile.python.output", pythonOutput);
        properties.setProperty("micronaut.dev.compile.java.output", javaOutput);
        properties.setProperty("micronaut.dev.compile.python.options", "-Apython.option=yes");
        properties.setProperty("micronaut.dev.compile.java.options", "-source,21,-target,21,-parameters");
        return DevManifest.of(project, properties);
    }

    private static Map<SourceKind, SourceCompiler> compilers(SourceCompiler... compilers) {
        Map<SourceKind, SourceCompiler> map = new EnumMap<>(SourceKind.class);
        for (SourceCompiler compiler : compilers) {
            for (SourceKind kind : compiler.kinds()) {
                map.put(kind, compiler);
            }
        }
        return map;
    }

    private static final class RecordingCompiler implements SourceCompiler {
        private final SourceKind kind;
        private final Set<SourceKind> joint;
        private final List<CompilationRequest> requests = new ArrayList<>();

        RecordingCompiler(SourceKind kind, Set<SourceKind> joint) {
            this.kind = kind;
            this.joint = joint;
        }

        @Override
        public Set<SourceKind> kinds() {
            return Set.of(kind);
        }

        @Override
        public Set<SourceKind> jointKinds() {
            return joint;
        }

        @Override
        public CompilationResult compile(CompilationRequest request) {
            requests.add(request);
            return new CompilationResult(CompilationResult.Status.SUCCESS, List.of(), Set.of(), Set.of(), Duration.ZERO);
        }
    }
}
