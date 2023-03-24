module micronaut.core.processor {
    requires micronaut.aop;
    requires micronaut.core;
    requires micronaut.inject;
    requires org.objectweb.asm;
    requires com.github.javaparser.core;
    requires org.objectweb.asm.commons;
    requires java.compiler;
    exports io.micronaut.inject.ast;
    exports io.micronaut.inject.visitor;
}
