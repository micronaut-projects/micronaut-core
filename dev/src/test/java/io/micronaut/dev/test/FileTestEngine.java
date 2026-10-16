package io.micronaut.dev.test;

import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.DirectorySelector;
import org.junit.platform.engine.discovery.FileSelector;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * An engine that discovers its tests from source files, as pytest's does: one test per {@code .py} file of a selected
 * directory or a selected file, grouped under the file's path, which passes when the file says {@code ok}. Registered
 * only on the loader of the requests that use it.
 */
public final class FileTestEngine implements TestEngine {

    static final String ID = "file-tests";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
        EngineDescriptor engine = new EngineDescriptor(uniqueId, "File tests");
        for (DirectorySelector directory : request.getSelectorsByType(DirectorySelector.class)) {
            try (Stream<Path> files = Files.list(directory.getPath())) {
                files.filter(file -> file.toString().endsWith(".py")).sorted()
                    .forEach(file -> engine.addChild(new FileTest(uniqueId, directory.getPath().getParent(), file)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        for (FileSelector file : request.getSelectorsByType(FileSelector.class)) {
            Path path = file.getPath();
            engine.addChild(new FileTest(uniqueId, path.getParent().getParent(), path));
        }
        return engine;
    }

    @Override
    public void execute(ExecutionRequest request) {
        TestDescriptor root = request.getRootTestDescriptor();
        request.getEngineExecutionListener().executionStarted(root);
        for (TestDescriptor child : root.getChildren()) {
            request.getEngineExecutionListener().executionStarted(child);
            TestExecutionResult result;
            try {
                result = Files.readString(((FileTest) child).file).contains("ok")
                    ? TestExecutionResult.successful()
                    : TestExecutionResult.failed(new AssertionError("not ok"));
            } catch (IOException e) {
                result = TestExecutionResult.failed(e);
            }
            request.getEngineExecutionListener().executionFinished(child, result);
        }
        request.getEngineExecutionListener().executionFinished(root, TestExecutionResult.successful());
    }

    private static final class FileTest extends AbstractTestDescriptor {
        private final Path file;

        FileTest(UniqueId engine, Path base, Path file) {
            // named as pytest names a function's source: the file's path relative to the directory holding the tests
            super(engine.append("file", file.toString()), file.getFileName().toString(),
                MethodSource.from(base.relativize(file).toString().replace('\\', '/'), "test"));
            this.file = file;
        }

        @Override
        public Type getType() {
            return Type.TEST;
        }
    }
}
