package org.particleframework.inject.method;

import javax.inject.Inject;


public class JavaClass {

    private JavaInterface javaInterface;

    @Inject
    public void setJavaInterface(JavaInterface javaInterface) {
        this.javaInterface = javaInterface;
    }

    public JavaInterface getJavaInterface() {
        return javaInterface;
    }
}
