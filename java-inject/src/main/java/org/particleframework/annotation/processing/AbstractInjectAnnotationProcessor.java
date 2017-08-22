package org.particleframework.annotation.processing;

import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Options;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.File;

abstract class AbstractInjectAnnotationProcessor extends AbstractProcessor {

    protected Messager messager;
    protected Filer filer;
    protected Elements elementUtils;
    protected Types typeUtils;
    protected AnnotationUtils annotationUtils;
    protected GenericUtils genericUtils;
    protected ModelUtils modelUtils;
    protected File targetDirectory;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.modelUtils = new ModelUtils(elementUtils,typeUtils);
        this.annotationUtils = new AnnotationUtils(elementUtils);
        this.genericUtils = new GenericUtils(elementUtils,typeUtils);

        Options javacOptions = Options.instance(((JavacProcessingEnvironment)processingEnv).getContext());
        this.targetDirectory = new File(javacOptions.get(Option.D));
    }

    protected void error(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }
    protected void error(String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args));
    }

    protected void note(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args), e);
    }

    protected void note(String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args));
    }
}
