package org.particleframework.annotation.processing;

import org.particleframework.inject.writer.ClassWriterOutputVisitor;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

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
    public File visitServiceDescriptor(String classname) throws IOException {
        return targetDirectory;
    }
}
