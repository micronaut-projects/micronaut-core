package org.gradle.api.micronaut;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;

/**
 * A filer in a package of Gradle, which Micronaut takes for the filer Gradle wraps around the filer of an incremental
 * annotation processor, and passes the originating elements to the way it passes them to Gradle.
 */
public final class GradleNamedFiler implements Filer {

    private final Filer filer;

    /**
     * @param filer The filer the files are written with
     */
    public GradleNamedFiler(Filer filer) {
        this.filer = filer;
    }

    @Override
    public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
        return filer.createSourceFile(name, originatingElements);
    }

    @Override
    public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
        return filer.createClassFile(name, originatingElements);
    }

    @Override
    public FileObject createResource(JavaFileManager.Location location, CharSequence moduleAndPkg, CharSequence relativeName, Element... originatingElements) throws IOException {
        return filer.createResource(location, moduleAndPkg, relativeName, originatingElements);
    }

    @Override
    public FileObject getResource(JavaFileManager.Location location, CharSequence moduleAndPkg, CharSequence relativeName) throws IOException {
        return filer.getResource(location, moduleAndPkg, relativeName);
    }
}
