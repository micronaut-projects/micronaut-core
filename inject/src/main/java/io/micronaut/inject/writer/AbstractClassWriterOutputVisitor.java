/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
        Map<String, Set<String>> serviceEntries = getServiceEntries();

        writeServiceEntries(serviceEntries);
    }

    /**
     * Writes the service entries.
     *
     * @param serviceEntries The service entries
     */
    public void writeServiceEntries(Map<String, Set<String>> serviceEntries) {
        for (Map.Entry<String, Set<String>> entry : serviceEntries.entrySet()) {
            String serviceName = entry.getKey();
            Set<String> serviceTypes = entry.getValue();

            Optional<GeneratedFile> serviceFile = visitMetaInfFile("services/" + serviceName);
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
        String message = e.getMessage();
        return !message.contains("does not exist") || !e.getClass().getName().equals("org.eclipse.core.internal.resources.ResourceException");
    }

}
