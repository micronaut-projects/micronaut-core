package org.particleframework.inject.reader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.particleframework.context.exceptions.BeanContextException;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.writer.BeanDefinitionWriter;

import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.inject.Scope;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by graemerocher on 26/05/2017.
 *
 * TODO: not finished
 */
public class BeanDefinitionReader {

    private final ClassLoader classLoader;
    private final Map<String, byte[]> runtimeBeanDefinitions = new ConcurrentHashMap<>();

    public BeanDefinitionReader(ClassLoader parentClassLoader) {
        this.classLoader = new BeanDefinitionClassLoader(parentClassLoader);
    }

    /**
     * Reads a bean definition for the given type
     *
     * @param type The class type
     * @return The bean definition
     */
    public <T> BeanDefinition<T> readBeanDefinition(Class<T> type) {
        String className = type.getName();
        String classAsPath = className.replace('.', '/') + ".class";
        Annotation scope = AnnotationUtil.findAnnotationWithStereoType(type, Scope.class);
        boolean isSingleton = AnnotationUtil.findAnnotationWithStereoType(type, Scope.class) != null;
        String scopeAnnotationName = scope != null ? scope.getClass().getName() : null;

        String packageName = type.getPackage().getName();
        String nameWithoutPackage = type.getName().substring(packageName.length() + 1, type.getName().length());
        BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(
                packageName,
                nameWithoutPackage,
                scopeAnnotationName,
                isSingleton);
        try {
            try (InputStream input = type.getClassLoader().getResourceAsStream(classAsPath)) {

                ClassReader classReader = new ClassReader(input);
                ClassNode classNode = new ClassNode();
                classReader.accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);

                List methods = classNode.methods;

                for (Object method : methods) {
                    MethodNode methodNode = (MethodNode) method;
                    boolean isConstructor = methodNode.name.equals("<init>");
                    List visibleAnnotations = methodNode.visibleAnnotations;
                    if (visibleAnnotations == null) continue;
                    for (Object visibleAnnotation : visibleAnnotations) {
                        AnnotationNode annotationNode = (AnnotationNode) visibleAnnotation;
                        Type annotationType = Type.getType(annotationNode.desc);
                        if (isConstructor && Inject.class.getName().equals(annotationType.getClassName())) {
                            String desc = methodNode.desc;
                            String genericSignature = methodNode.signature;
                            Type[] argumentTypes = Type.getArgumentTypes(desc);
                            List[] visibleParameterAnnotations = methodNode.visibleParameterAnnotations;
                            Map<String, Object> argMap = new LinkedHashMap<>();
                            Map<String, Object> qualifierMap = new LinkedHashMap<>();
                            for (int i = 0; i < argumentTypes.length; i++) {
                                List parameterAnnotations = visibleParameterAnnotations != null ? visibleParameterAnnotations[i] : null;
                                Class qualifier = findQualifierType(parameterAnnotations);
                                Type argumentType = argumentTypes[i];
//                                Type genericArgumentType = genericArgumentTypes != null ? genericArgumentTypes[i] : null;
//                                if(genericArgumentType != null) {
//
//                                    System.out.println("genericArgumentType.getArgumentTypes() = " + genericArgumentType.getArgumentTypes());
//                                }

                                String argName = "arg" + i;
                                argMap.put(argName, argumentType.getClassName());
                                if(qualifier != null) {
                                    qualifierMap.put(argName, qualifier);
                                }
                            }
                            beanDefinitionWriter.visitBeanDefinitionConstructor(
                                    argMap, qualifierMap.isEmpty() ? null : qualifierMap, null
                            );
                            break;
                        }
                    }
                }
            }

            beanDefinitionWriter.visitBeanDefinitionEnd();
            byte[] classBytes = beanDefinitionWriter.toByteArray();
//            ClassReader classReader = new ClassReader(classBytes);
//            TraceClassVisitor traceClassVisitor = new TraceClassVisitor(new PrintWriter(System.out));
//            classReader.accept(traceClassVisitor, 0);

            String newClassName = beanDefinitionWriter.getBeanDefinitionName();
            runtimeBeanDefinitions.put(newClassName, classBytes);
            Class<?> beanDefinitionClass = classLoader.loadClass(newClassName);
            return (BeanDefinition<T>) beanDefinitionClass.newInstance();
        } catch (Throwable e) {
            throw new BeanContextException("Cannot define bean definition at runtime for type [" + type + "]: " + e.getMessage(), e);
        }
    }

    private Class findQualifierType(List<AnnotationNode> parameterAnnotations) {
        if(parameterAnnotations != null) {
            for (AnnotationNode annotationNode : parameterAnnotations) {
                String className = Type.getType(annotationNode.desc).getClassName();
                Class<?> annotationClass = ClassUtils.forName(className, classLoader)
                                                        .orElse(null);
                if( annotationClass != null && AnnotationUtil.findAnnotationWithStereoType(annotationClass, Qualifier.class) != null)  {
                    return annotationClass;
                }
            }
        }
        return null;
    }

    private class BeanDefinitionClassLoader extends ClassLoader {
        public BeanDefinitionClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = runtimeBeanDefinitions.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            } else {
                return super.findClass(name);
            }
        }
    }
}
