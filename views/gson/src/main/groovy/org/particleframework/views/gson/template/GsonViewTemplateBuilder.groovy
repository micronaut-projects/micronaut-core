package org.particleframework.views.gson.template

import groovy.transform.CompileStatic

import java.lang.reflect.Field
import java.lang.reflect.Method

class GsonViewTemplateBuilder {

    Writable make();

    Writable make(Map var1);

    /**
     * The class of the template
     */
    final Class<? extends GrailsView> templateClass
    /**
     * The resolved template path
     */
    String templatePath
    /**
     * The source file of the template. Will be null in pre-compiled mode.
     */
    File sourceFile
    /**
     * Whether to pretty print the template
     */
    boolean prettyPrint = false
    /**
     * The last modified stamp of the source file. -1 if no source file.
     */
    long lastModified = -1



    protected final Map<String, VariableSetter> modelSetters = [:]

    WritableScriptTemplate(Class<? extends GrailsView> templateClass) {
        this(templateClass, null)
    }

    WritableScriptTemplate(Class<? extends GrailsView> templateClass, File sourceFile) {
        this.templateClass = templateClass
        this.sourceFile = sourceFile
        if(sourceFile != null) {
            lastModified = sourceFile.lastModified()
        }
        initModelTypes(templateClass)
    }

    /**
     * The path to the parent directory that containers the template
     */
    String getParentPath() {
        if(templatePath != null) {
            return templatePath.substring(0, templatePath.lastIndexOf('/'))
        }
        else {
            return "/"
        }
    }
    /**
     * @return Whether the template has been modified
     */
    boolean wasModified() {
        if(sourceFile != null && lastModified != -1) {
            return sourceFile.lastModified() > lastModified
        }
        return false
    }

    protected void initModelTypes(Class<? extends WritableScript> templateClass) {
        def field = ReflectionUtils.findField(templateClass, Views.MODEL_TYPES_FIELD)

        if(field != null) {

            field.setAccessible(true)

            def modelTypes = (Map<String, Class>) field.get(templateClass)
            if(modelTypes != null) {
                for(mt in modelTypes) {
                    def propertyName = mt.key
                    def propertyField = ReflectionUtils.findField(templateClass, propertyName)
                    if(propertyField != null) {
                        propertyField.setAccessible(true)
                        modelSetters.put(propertyName, new FieldSetter(propertyField) )
                    }
                    else {

                        def setterName = GrailsNameUtils.getSetterName(propertyName)
                        def method = ReflectionUtils.findMethod(templateClass, setterName, mt.value)
                        if(method != null) {
                            method.setAccessible(true)
                            modelSetters.put(propertyName, new MethodSetter(mt.value, method))
                        }
                    }

                }
            }
        }
    }

    @Override
    Writable make() {
        make Collections.emptyMap()
    }

    @Override
    Writable make(Map binding) {

        WritableScript writableTemplate = templateClass
                .newInstance()
        writableTemplate.viewTemplate = (GrailsViewTemplate)this
        writableTemplate.prettyPrint = prettyPrint
        if(!binding.isEmpty()) {
            writableTemplate.binding = new Binding(binding)
            for(modelSetter in modelSetters.entrySet()) {
                def value = binding.get(modelSetter.key)
                if(value != null) {
                    VariableSetter setMethod = modelSetter.value
                    def exceptedType = setMethod.type
                    if( exceptedType.isInstance(value) || value == null ) {
                        setMethod.invoke(writableTemplate, value)
                    }
                    else {
                        throw new IllegalArgumentException("Model variable [$modelSetter.key] of with value [$value] type [${value?.getClass()?.name}] is not of the correct type [$exceptedType.name]")
                    }
                }
            }
        }
        writableTemplate.setSourceFile(sourceFile)
        return writableTemplate
    }

    private static interface VariableSetter {
        Class getType()
        void invoke(WritableScript template, value)
    }

    @CompileStatic
    private static class FieldSetter implements VariableSetter {

        final Class type
        final Field field

        FieldSetter(Field field) {
            this.type = field.type
            this.field = field
        }

        @Override
        void invoke(WritableScript template, Object value) {
            field.set(template, value)
        }
    }

    @CompileStatic
    private static class MethodSetter implements VariableSetter {

        final Class type
        final Method method

        MethodSetter(Class type, Method method) {
            this.type = type
            this.method = method
        }

        @Override
        void invoke(WritableScript template, Object value) {
            method.invoke(template, value)
        }
    }
}
