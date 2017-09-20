/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.aop.writer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.particleframework.aop.Intercepted;
import org.particleframework.aop.Interceptor;
import org.particleframework.inject.writer.AbstractClassFileWriter;
import org.particleframework.inject.writer.BeanDefinitionWriter;
import org.particleframework.inject.writer.ClassGenerationException;

import java.io.File;
import java.io.OutputStream;
import java.util.*;

import static org.particleframework.inject.writer.BeanDefinitionWriter.DEFAULT_MAX_STACK;

/**
 * A class that generates AOP classes at compile time
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AopProxyWriter extends AbstractClassFileWriter {
    private final String packageName;
    private final String targetClassShortName;
    private final ClassWriter classWriter;
    private final String targetClassFullName;
    private final String proxyFullName;
    private final BeanDefinitionWriter proxyBeanDefinitionWriter;
    private final String proxyInternalName;
    private final Object[] interceptorTypes;

    private MethodVisitor constructorWriter;

    public AopProxyWriter(String packageName, String targetClassShortName, Object... interceptorTypes) {
        this.packageName = packageName;
        this.targetClassShortName = targetClassShortName;
        this.targetClassFullName = packageName + '.' + targetClassShortName;
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        String proxyShortName = targetClassShortName + "$Intercepted";
        this.proxyFullName = packageName + '.' + proxyShortName;
        this.proxyInternalName = getInternalName(this.proxyFullName);
        this.interceptorTypes = interceptorTypes;
        // TODO: propagate scopes
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(packageName, proxyShortName, null, true);
        startClass(classWriter, proxyFullName, getTypeReference(targetClassFullName));
    }

    /**
     * @return The bean definition writer for this proxy
     */
    public BeanDefinitionWriter getProxyBeanDefinitionWriter() {
        return proxyBeanDefinitionWriter;
    }

    /**
     * Visits a no arguments constructor. Either this method or {@link #visitConstructor(Map, Map, Map)} should be called at least once
     */
    public void visitConstructor() {
        visitConstructor(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Visits a constructor with arguments. Either this method or {@link #visitConstructor()} should be called at least once
     *
     * @param argumentTypes The argument names and types. Should be an ordered map should as {@link LinkedHashMap}
     * @param qualifierTypes The argument names and qualifier types. Should be an ordered map should as {@link LinkedHashMap}
     * @param genericTypes The argument names and generic types. Should be an ordered map should as {@link LinkedHashMap}
     */
    public void visitConstructor(Map<String, Object> argumentTypes, Map<String, Object> qualifierTypes, Map<String, List<Object>> genericTypes) {
        Map<String,Object> newArgumentTypes = new LinkedHashMap<>(argumentTypes);
        newArgumentTypes.put("interceptors", Interceptor[].class);

        String constructorDescriptor = getConstructorDescriptor(newArgumentTypes.values());
        this.constructorWriter = classWriter.visitMethod(ACC_PUBLIC, "<init>", constructorDescriptor, null, null);
        GeneratorAdapter adapter = new GeneratorAdapter(constructorWriter, Opcodes.ACC_PUBLIC, "<init>", constructorDescriptor);
        adapter.loadThis();
        Collection<Object> existingArguments = argumentTypes.values();
        for (int i = 0; i < existingArguments.size(); i++) {
            adapter.loadArg(i);
        }
        String superConstructorDescriptor = getConstructorDescriptor(existingArguments);
        adapter.invokeConstructor(getTypeReference(targetClassFullName), new Method("<init>", superConstructorDescriptor));
        proxyBeanDefinitionWriter.visitBeanDefinitionConstructor(newArgumentTypes, qualifierTypes, genericTypes);
    }

    public void visitProxyEnd() {
        classWriter.visit(V1_8, ACC_PUBLIC,
                proxyInternalName,
                null,
                getTypeReference(targetClassFullName).getInternalName(),
                new String[]{Type.getInternalName(Intercepted.class)});

        if(constructorWriter == null) {
            throw new IllegalStateException("The method visitConstructor(..) should be called at least once");
        }

        constructorWriter.visitInsn(RETURN);
        constructorWriter.visitMaxs(DEFAULT_MAX_STACK, 1);

        this.constructorWriter.visitEnd();
        proxyBeanDefinitionWriter.visitBeanDefinitionEnd();
        classWriter.visitEnd();
    }
    /**
     * Write the class to the target directory
     *
     * @param targetDir The target directory
     */
    public void writeTo(File targetDir) {
        try {

            writeClassToDisk(targetDir, classWriter, proxyInternalName);

        } catch (Throwable e) {
            throw new ClassGenerationException("Error generating proxy definition class for bean definition ["+targetClassFullName+"]: " + e.getMessage(), e);
        }
    }

    /**
     * Write the class to the output stream, such a JavaFileObject created from a java annotation processor Filer object
     *
     * @param outputStream the output stream pointing to the target class file
     */
    public void writeTo(OutputStream outputStream) {
        try {
            writeClassToDisk(outputStream, classWriter);

        } catch (Throwable e) {
            throw new ClassGenerationException("Error generating bean definition class for bean definition ["+targetClassFullName+"]: " + e.getMessage(), e);
        }
    }

}
