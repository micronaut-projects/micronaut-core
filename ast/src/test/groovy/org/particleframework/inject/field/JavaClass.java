package org.particleframework.inject.field;

import org.particleframework.context.annotation.Configuration;

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
