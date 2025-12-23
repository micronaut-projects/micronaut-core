/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.test.pytest;

import io.micronaut.context.python.ContextHolder;
import io.micronaut.context.python.GraalPyContextFactory;
import io.micronaut.core.util.StringUtils;
import io.micronaut.test.pytest.discovery.PytestDiscoverySelectorResolver;
import io.micronaut.test.pytest.execution.PytestTestExecutor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DirectorySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.PackageNameFilter;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;


/**
 * JUnit 5 TestEngine implementation for running pytest tests using GraalPy.
 * This engine discovers and executes Python tests written with pytest framework.
 */
public class PytestTestEngine implements TestEngine {

    private static final Logger LOG = LoggerFactory.getLogger(PytestTestEngine.class);

    private static final String ENGINE_ID = "pytest-engine";
    public static final String TEST_SOURCE_DIR = "pytest.src.dir";
    private Context context;


    @Override
    public String getId() {
        return ENGINE_ID;
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
        LOG.debug("Starting test discovery with uniqueId: {}", uniqueId);
        ConfigurationParameters configurationParameters = discoveryRequest.getConfigurationParameters();

        if (this.context == null) {
            System.setProperty("org.graalvm.python.vfs.allow_multiple", StringUtils.TRUE);
            var pyEnv = System.getenv("PYENV_VERSION");
            var venv = System.getenv("VIRTUAL_ENV");
            Context.Builder builder = GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                    .resourceDirectory(GraalPyContextFactory.APPLICATION_PATH)
                    .resourceLoadingClass(PytestTestEngine.class)
                    .build())
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(name -> true);
            if (pyEnv != null && venv != null && pyEnv.startsWith("graalpy")) {
                builder.option("python.Executable", Path.of(venv).resolve("bin/python").toString());
            }
            configurationParameters.keySet().forEach(key -> {
                if (key.startsWith("python.")) {
                    configurationParameters.get(key).ifPresent(value ->
                        builder.option(key, value)
                    );
                }
            });
            this.context = builder.build();
            ContextHolder.setContext(context);
        }

        EngineDescriptor engineDescriptor = new EngineDescriptor(uniqueId, "Micronaut Pytest Engine");

        PytestDiscoverySelectorResolver selectorResolver = new PytestDiscoverySelectorResolver(context);

        String testSrc = configurationParameters.get(TEST_SOURCE_DIR).orElse(null);
        if (testSrc != null) {
            Path srcPath = Paths.get(testSrc);
            if (Files.exists(srcPath)) {
                DirectorySelector directorySelector = DiscoverySelectors.selectDirectory(testSrc);
                selectorResolver.resolveSelectors(directorySelector, engineDescriptor);
            }
        }

        // Process discovery selectors
        discoveryRequest.getSelectorsByType(DiscoverySelector.class).forEach(selector -> {
            LOG.debug("Processing selector: {}", selector);
            selectorResolver.resolveSelectors(selector, engineDescriptor);
        });

        // Apply filters
        discoveryRequest.getFiltersByType(ClassNameFilter.class).forEach(filter -> {
            LOG.debug("Applying class name filter: {}", filter);
            // TODO: Implement class name filtering
        });

        discoveryRequest.getFiltersByType(PackageNameFilter.class).forEach(filter -> {
            LOG.debug("Applying package name filter: {}", filter);
            // TODO: Implement package name filtering
        });

        LOG.debug("Discovery completed. Found {} test descriptors", engineDescriptor.getChildren().size());

        return engineDescriptor;
    }

    @Override
    public void execute(ExecutionRequest request) {
        if (context != null) {
            LOG.debug("Starting test execution");

            TestDescriptor rootDescriptor = request.getRootTestDescriptor();
            PytestTestExecutor executor = new PytestTestExecutor(this.context, request.getEngineExecutionListener());

            try {
                executor.execute(rootDescriptor);
                LOG.debug("Test execution completed successfully");
            } catch (Exception e) {
                LOG.error("Error during test execution", e);
                throw e;
            } finally {
                try {
                    context.close();
                    context = null;
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public Optional<String> getGroupId() {
        return Optional.of("pyronaut.test");
    }

    @Override
    public Optional<String> getArtifactId() {
        return Optional.of("micronaut-pytest-engine");
    }
}
