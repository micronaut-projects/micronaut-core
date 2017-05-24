package org.particleframework.inject.asm;

import groovyjarjarasm.asm.ClassWriter;

/**
 * Created by graemerocher on 22/05/2017.
 */
public class BeanDefinitionWriter extends AbstractClassFileWriter {

    private final ClassWriter classWriter;
    private final String beanType;

    public BeanDefinitionWriter(String beanType) {
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        this.beanType = beanType;
    }

    void startBeanDefinition() {

    }

    void finalizeBeanDefinition() {

    }
}
