package org.particleframework.inject.writer;

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
     * @return the output directory
     * @throws IOException
     */
    Optional<File> visitServiceDescriptor(String classname) throws IOException;
}
