package io.micronaut.inject.writer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

public interface ClassWriterOutputVisitor {
    /**
     *
     * @param classname the fully qualified classname
     * @return the output stream to write to
     * @throws IOException if an error occurs creating the output stream
     */
    OutputStream visitClass(String classname) throws IOException;

    /**
     *
     * @param classname the fully qualified classname
     * @return An optional file it was possible to create it
     * @throws IOException
     */
    Optional<File> visitServiceDescriptor(String classname) throws IOException;

    /**
     * Visit a file within the META-INF directory
     * @param path The path to the file
     * @return An optional file it was possible to create it
     * @throws IOException
     */
    Optional<File> visitMetaInfFile(String path) throws IOException;
    /**
     * @param type The service type
     * @return the output directory
     * @throws IOException
     */
    default Optional<File> visitServiceDescriptor(Class type) throws IOException {
        return visitServiceDescriptor(type.getName());
    }
}
