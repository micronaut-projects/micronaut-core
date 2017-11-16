package org.particleframework.annotation.processing;

import org.particleframework.core.io.service.SoftServiceLoader;
import org.particleframework.inject.writer.ClassWriterOutputVisitor;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

public class BeanDefinitionWriterVisitor implements ClassWriterOutputVisitor {

    private final File targetDirectory;
    private final Filer filer;

    BeanDefinitionWriterVisitor(Filer filer,  File targetDirectory) {
        this.targetDirectory = targetDirectory;
        this.filer = filer;
    }

    @Override
    public OutputStream visitClass(String classname) throws IOException {
        JavaFileObject javaFileObject = filer.createClassFile(classname);
        return javaFileObject.openOutputStream();
    }

    @Override
    public Optional<File> visitServiceDescriptor(String classname) throws IOException {
        return Optional.ofNullable(targetDirectory).map(root ->
            new File(root, SoftServiceLoader.META_INF_SERVICES + File.separator + classname)
        );
    }
}
