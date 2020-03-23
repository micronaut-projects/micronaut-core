package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.configuration.ConfigurationMetadata;
import io.micronaut.inject.configuration.ConfigurationMetadataWriter;
import io.micronaut.inject.writer.ClassGenerationException;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.TypeElement;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 *
 */
@Internal
@SupportedOptions({AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_INCREMENTAL, AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_ANNOTATIONS})
public class MetadataProcessor extends AbstractInjectAnnotationProcessor {

    private static JavaConfigurationMetadataBuilder metadataBuilder;
    private static ClassWriterOutputVisitor outputVisitor;

    @Override
    public Set<String> getSupportedOptions() {
        return CollectionUtils.setOf("org.gradle.annotation.processing.aggregating");
    }

    /**
     * Set the metadata builder for this round.
     * @param metadataBuilder The metadata builder
     */
    public static void setMetadataBuilder(JavaConfigurationMetadataBuilder metadataBuilder) {
        MetadataProcessor.metadataBuilder = metadataBuilder;
    }

    /**
     * Set the output visitor for this round.
     * @param outputVisitor The output visitor
     */
    public static void setOutputVisitor(ClassWriterOutputVisitor outputVisitor) {
        MetadataProcessor.outputVisitor = outputVisitor;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (outputVisitor != null && !roundEnv.errorRaised()) {
            try {
                Map<String, Set<String>> serviceEntries = outputVisitor.getServiceEntries();
                for (Map.Entry<String, Set<String>> entry : serviceEntries.entrySet()) {
                    String serviceName = entry.getKey();
                    Set<String> serviceTypes = entry.getValue();

                    Optional<GeneratedFile> serviceFile = classWriterOutputVisitor.visitMetaInfFile("services/" + serviceName);
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

                writeConfigurationMetadata();
            } finally {
                outputVisitor = null;
                metadataBuilder = null;
            }
        }
        return true;
    }

    private void writeConfigurationMetadata() {
        if (metadataBuilder != null && metadataBuilder.hasMetadata()) {
            ServiceLoader<ConfigurationMetadataWriter> writers = ServiceLoader.load(ConfigurationMetadataWriter.class, getClass().getClassLoader());

            try {
                for (ConfigurationMetadataWriter writer : writers) {
                    try {
                        writer.write(metadataBuilder, classWriterOutputVisitor);
                    } catch (IOException e) {
                        error("Error occurred writing configuration metadata: %s", e.getMessage());
                    }
                }
            } catch (ServiceConfigurationError e) {
                warning("Unable to load ConfigurationMetadataWriter due to : %s", e.getMessage());
            }
        }
    }

    private boolean isNotEclipseNotFound(Throwable e) {
        String message = e.getMessage();
        return !message.contains("does not exist") || !e.getClass().getName().equals("org.eclipse.core.internal.resources.ResourceException");
    }
}
