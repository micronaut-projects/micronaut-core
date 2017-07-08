package org.particleframework.inject.field;

import javax.inject.Inject;

public class JavaClass {

    @Inject
    private JavaInterface javaInterface;

    public JavaInterface getJavaInterface() {
        return javaInterface;
    }
}
