
package org.particleframework.aop;

import java.util.*;

import org.objectweb.asm.*;

public class FooJava$InterceptedDump implements Opcodes {

    public static byte[] dump() throws Exception {

        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "org/particleframework/aop/FooJava$Intercepted", null, "org/particleframework/aop/Foo", new String[]{"org/particleframework/aop/Intercepted"});

        cw.visitSource("FooJava$Intercepted.java", null);

        cw.visitInnerClass("org/particleframework/aop/FooJava$Intercepted$$blah0", "org/particleframework/aop/FooJava$Intercepted", "$blah0", 0);

        {
            fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "interceptors", "[[Lorg/particleframework/aop/Interceptor;", null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "proxyMethods", "[Lorg/particleframework/inject/ExecutableMethod;", null, null);
            fv.visitEnd();
        }
        {
            mv = cw.visitMethod(0, "<init>", "(I[Lorg/particleframework/aop/Interceptor;)V", null, new String[]{"java/lang/NoSuchMethodException"});
            {
                av0 = mv.visitParameterAnnotation(1, "Lorg/particleframework/context/annotation/Type;", true);
                {
                    AnnotationVisitor av1 = av0.visitArray("value");
                    av1.visit(null, Type.getType("Lorg/particleframework/aop/Mutating;"));
                    av1.visit(null, Type.getType("Lorg/particleframework/aop/annotation/Trace;"));
                    av1.visitEnd();
                }
                av0.visitEnd();
            }
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(37, l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, "org/particleframework/aop/Foo", "<init>", "(I)V", false);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLineNumber(38, l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "[Lorg/particleframework/aop/Interceptor;");
            mv.visitFieldInsn(PUTFIELD, "org/particleframework/aop/FooJava$Intercepted", "interceptors", "[[Lorg/particleframework/aop/Interceptor;");
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLineNumber(39, l2);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "org/particleframework/inject/ExecutableMethod");
            mv.visitFieldInsn(PUTFIELD, "org/particleframework/aop/FooJava$Intercepted", "proxyMethods", "[Lorg/particleframework/inject/ExecutableMethod;");
            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitLineNumber(40, l3);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, "org/particleframework/aop/FooJava$Intercepted", "proxyMethods", "[Lorg/particleframework/inject/ExecutableMethod;");
            mv.visitInsn(ICONST_0);
            mv.visitTypeInsn(NEW, "org/particleframework/aop/FooJava$Intercepted$$blah0");
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "org/particleframework/aop/FooJava$Intercepted$$blah0", "<init>", "(Lorg/particleframework/aop/FooJava$Intercepted;)V", false);
            mv.visitInsn(AASTORE);
            Label l4 = new Label();
            mv.visitLabel(l4);
            mv.visitLineNumber(41, l4);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, "org/particleframework/aop/FooJava$Intercepted", "interceptors", "[[Lorg/particleframework/aop/Interceptor;");
            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, "org/particleframework/aop/FooJava$Intercepted", "proxyMethods", "[Lorg/particleframework/inject/ExecutableMethod;");
            mv.visitInsn(ICONST_0);
            mv.visitInsn(AALOAD);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(INVOKESTATIC, "org/particleframework/aop/internal/InterceptorChain", "resolveInterceptors", "(Ljava/lang/reflect/AnnotatedElement;[Lorg/particleframework/aop/Interceptor;)[Lorg/particleframework/aop/Interceptor;", false);
            mv.visitInsn(AASTORE);
            Label l5 = new Label();
            mv.visitLabel(l5);
            mv.visitLineNumber(42, l5);
            mv.visitInsn(RETURN);
            Label l6 = new Label();
            mv.visitLabel(l6);
            mv.visitLocalVariable("this", "Lorg/particleframework/aop/FooJava$Intercepted;", null, l0, l6, 0);
            mv.visitLocalVariable("c", "I", null, l0, l6, 1);
            mv.visitLocalVariable("interceptors", "[Lorg/particleframework/aop/Interceptor;", null, l0, l6, 2);
            mv.visitMaxs(5, 3);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "blah", "(Ljava/lang/String;)Ljava/lang/String;", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(46, l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, "org/particleframework/aop/FooJava$Intercepted", "proxyMethods", "[Lorg/particleframework/inject/ExecutableMethod;");
            mv.visitInsn(ICONST_0);
            mv.visitInsn(AALOAD);
            mv.visitVarInsn(ASTORE, 2);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLineNumber(47, l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, "org/particleframework/aop/FooJava$Intercepted", "interceptors", "[[Lorg/particleframework/aop/Interceptor;");
            mv.visitInsn(ICONST_0);
            mv.visitInsn(AALOAD);
            mv.visitVarInsn(ASTORE, 3);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLineNumber(48, l2);
            mv.visitTypeInsn(NEW, "org/particleframework/aop/internal/MethodInterceptorChain");
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInsn(AASTORE);
            mv.visitMethodInsn(INVOKESPECIAL, "org/particleframework/aop/internal/MethodInterceptorChain", "<init>", "([Lorg/particleframework/aop/Interceptor;Ljava/lang/Object;Lorg/particleframework/inject/ExecutableMethod;[Ljava/lang/Object;)V", false);
            mv.visitVarInsn(ASTORE, 4);
            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitLineNumber(49, l3);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKEVIRTUAL, "org/particleframework/aop/internal/InterceptorChain", "proceed", "()Ljava/lang/Object;", false);
            mv.visitTypeInsn(CHECKCAST, "java/lang/String");
            mv.visitInsn(ARETURN);
            Label l4 = new Label();
            mv.visitLabel(l4);
            mv.visitLocalVariable("this", "Lorg/particleframework/aop/FooJava$Intercepted;", null, l0, l4, 0);
            mv.visitLocalVariable("name", "Ljava/lang/String;", null, l0, l4, 1);
            mv.visitLocalVariable("executableMethod", "Lorg/particleframework/inject/ExecutableMethod;", null, l1, l4, 2);
            mv.visitLocalVariable("interceptors", "[Lorg/particleframework/aop/Interceptor;", null, l2, l4, 3);
            mv.visitLocalVariable("chain", "Lorg/particleframework/aop/internal/InterceptorChain;", null, l3, l4, 4);
            mv.visitMaxs(9, 5);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_STATIC + ACC_SYNTHETIC, "access$001", "(Lorg/particleframework/aop/FooJava$Intercepted;Ljava/lang/String;)Ljava/lang/String;", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(32, l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, "org/particleframework/aop/Foo", "blah", "(Ljava/lang/String;)Ljava/lang/String;", false);
            mv.visitInsn(ARETURN);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLocalVariable("x0", "Lorg/particleframework/aop/FooJava$Intercepted;", null, l0, l1, 0);
            mv.visitLocalVariable("x1", "Ljava/lang/String;", null, l0, l1, 1);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }
}
