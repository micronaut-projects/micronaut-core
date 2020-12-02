/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.Element;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Abstract implementation of the {@link ClassWriterOutputVisitor} interface that deals with service descriptors in a
 * common way across Java and Groovy.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public abstract class AbstractClassWriterOutputVisitor implements ClassWriterOutputVisitor {
    private final Map<String, Set<String>> serviceDescriptors = new HashMap<>();
    private final boolean isWriteOnFinish;

    /**
     * Default constructor.
     * @param isWriteOnFinish Is this the eclipse compiler
     */
    protected AbstractClassWriterOutputVisitor(boolean isWriteOnFinish) {
        this.isWriteOnFinish = isWriteOnFinish;
    }

    /**
     * Compatibility constructor.
     */
    public AbstractClassWriterOutputVisitor() {
        this.isWriteOnFinish = false;
    }

    @Override
    public final Map<String, Set<String>> getServiceEntries() {
        return serviceDescriptors;
    }

    @Override
    public final void visitServiceDescriptor(String type, String classname) {
        if (StringUtils.isNotEmpty(type) && StringUtils.isNotEmpty(classname)) {
            serviceDescriptors.computeIfAbsent(type, s -> new HashSet<>()).add(classname);
        }
    }

    @Override
    public final void finish() {
        // for Java we only write out service entries for the Eclipse compiler because
        // for javac we support incremental compilation via ServiceDescriptionProcessor
        // this approach doesn't work in Eclipse.
        // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=567116
        // If the above issue is fixed then this workaround can be removed

        // for Groovy writing service entries is also required as ServiceDescriptionProcessor
        // is not triggered. See DirectoryClassWriterOutputVisitor
        if (isWriteOnFinish) {
            Map<String, Set<String>> serviceEntries = getServiceEntries();

            writeServiceEntries(serviceEntries);
        }
    }

    /**
     * Writes the service entries.
     *
     * @param serviceEntries The service entries
     * @param originatingElements The originating elements
     */
    public void writeServiceEntries(Map<String, Set<String>> serviceEntries, Element... originatingElements) {
        for (Map.Entry<String, Set<String>> entry : serviceEntries.entrySet()) {
            String serviceName = entry.getKey();
            Set<String> serviceTypes = entry.getValue();

            Optional<GeneratedFile> serviceFile = visitMetaInfFile("services/" + serviceName, originatingElements);
            if (serviceFile.isPresent()) {
                GeneratedFile generatedFile = serviceFile.get();

                // add the existing definitions
                try (BufferedReader bufferedReader = new BufferedReader(generatedFile.openReader())) {

                    String line = bufferedReader.readLine();
                    while (line != null) {
                        serviceTypes.add(line);
                        line = bufferedReader.readLine();
                    }
                } catch (FileNotFoundException | java.nio.file.NoSuchFileException x) {
                    // doesn't exist
                } catch (IOException x) {
                    Throwable cause = x.getCause();
                    if (isNotEclipseNotFound(cause)) {
                        throw new ClassGenerationException("Failed to load existing service definition files: " + x, x);
                    }
                } catch (Throwable e) {
                    // horrible hack to support Eclipse
                    if (isNotEclipseNotFound(e)) {
                        throw new ClassGenerationException("Failed to load existing service definition files: " + e, e);
                    }
                }

                // write out new definitions
                try (BufferedWriter writer = new BufferedWriter(generatedFile.openWriter())) {
                    for (String serviceType : serviceTypes) {
                        writer.write(serviceType);
                        writer.newLine();
                    }
                } catch (IOException x) {
                    throw new ClassGenerationException("Failed to open writer for service definition files: " + x);
                }
            }
        }
    }

    private boolean isNotEclipseNotFound(Throwable e) {
        if (isWriteOnFinish) {
            return false;
        }
        String message = e.getMessage();
        return !message.contains("does not exist") || !e.getClass().getName().startsWith("org.eclipse");
    }
}
