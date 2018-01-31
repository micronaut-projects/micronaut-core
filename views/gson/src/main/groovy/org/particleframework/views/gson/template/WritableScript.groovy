package org.particleframework.views.gson.template

interface WritableScript extends Writable {

    /**
     * Obtains the source file
     */
    File getSourceFile()
    /**
     * @param file Sets the source file
     */
    void setSourceFile(File file)

    /**
     * Sets the binding
     *
     * @param binding The binding
     */
    void setBinding(Binding binding)

    /**
     * @return Obtains the binding
     */
    Binding getBinding()

    /**
     * Runs the script and returns the result
     *
     * @return The result
     */
    Object run()

    /**
     * @return The current writer
     */
    Writer getOut()
}
