package org.particleframework.views.gson.template

import com.sun.corba.se.spi.ior.Writeable
import groovy.json.JsonGenerator
import groovy.json.JsonOutput
import groovy.json.StreamingJsonBuilder
import org.particleframework.views.gson.helper.GsonViewHelper
import org.particleframework.views.gson.helper.JsonChars

abstract class GsonViewBaseTemplate extends Script implements WritableScript {

    public static final String EXTENSION = "gson"
    public static final String TYPE = "view.gson"

    Object root
    boolean inline = false
    Writer out

    /**
     * Whether to pretty print
     */
    boolean prettyPrint = false


    /**
     * The default generator
     */
    JsonGenerator generator

    /**
     * The {@link StreamingJsonBuilder} instance
     */
    StreamingJsonBuilder json

    /**
     * The parent template if any
     */
    GsonViewBaseTemplate parentTemplate

    /**
     * The parent model, if any
     */
    Map parentModel
    /**
     * Overrides the default helper with new methods specific to JSON building
     */
    private GsonViewHelper viewHelper = new GsonViewHelper(this)

    /**
     * @return The default view helper
     */
    GsonViewHelper getG() {
        return viewHelper
    }
    /**
     * The source file
     */
    File sourceFile

    @Override
    Writer doWrite(Writer out) throws IOException {

        if(!prettyPrint) {
            this.json = new StreamingJsonBuilder(out, this.generator)
            run()
            return out
        }
        else {
            def writer = new StringWriter()
            setOut(writer)
            this.json = new StreamingJsonBuilder(writer, this.generator)
            run()
            def prettyOutput = JsonOutput.prettyPrint(writer.toString())
            out.write(prettyOutput)
            return out
        }
    }


    /**
     * TODO: When Groovy 2.4.5 go back to JsonBuilder from groovy-json
     *
     * @param callable
     * @return
     */
    StreamingJsonBuilder json(@DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure callable) {
        if(parentTemplate != null) {
            if (!inline) {
                out.write(JsonChars.OPEN_BRACE)
            }
            GsonViewBaseTemplate parentWritable = prepareParentWritable()
            parentWritable.writeTo(out)
            resetProcessedObjects()
            StreamingJsonBuilder.StreamingJsonDelegate.cloneDelegateAndGetContent()
            def jsonDelegate = new StreamingJsonBuilder.StreamingJsonDelegate(out, false, generator)
            callable.setDelegate(jsonDelegate)
            callable.call()
            if (!inline) {
                out.write(JsonChars.CLOSE_BRACE)
            }
        }
        else {

            this.root = callable
            if(inline) {
                def jsonDelegate = new StreamingJsonBuilder.StreamingJsonDelegate(out, true, generator)
                callable.setDelegate(jsonDelegate)
                callable.call()
            }
            else {
                json.call callable
            }
        }
        return json
    }

    StreamingJsonBuilder json(Iterable iterable) {
        this.root = iterable
        json.call iterable.asList()
        return json
    }

    StreamingJsonBuilder json(Map map) {
        this.root = map
        json.call map
        return json
    }

    /**
     * Print unescaped json directly
     *
     * @param unescaped The unescaped JSON produced from templates
     *
     * @return The json builder
     */
    StreamingJsonBuilder json(JsonOutput.JsonUnescaped unescaped) {
        print(unescaped.text)
        return json
    }

    /**
     * Print unescaped json directly
     *
     * @param writable The unescaped JSON produced from templates
     *
     * @return The json builder
     */
    StreamingJsonBuilder json(JsonOutput.JsonWritable writable) {
        if(parentTemplate != null) {
            out.write(JsonOutput.OPEN_BRACE)
            Writable parentWritable = prepareParentWritable()
            parentWritable.writeTo(out)
            resetProcessedObjects()
            writable.setInline(true)
            writable.setFirst(false)
            writable.writeTo(out)
            out.write(JsonOutput.CLOSE_BRACE)
        }
        else {
            writable.setInline(inline)
            writable.writeTo(out)
        }
        return json
    }

    /**
     * TODO: When Groovy 2.4.5 go back to JsonBuilder from groovy-json
     *
     * @param callable
     * @return
     */
    StreamingJsonBuilder json(Iterable iterable, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure callable) {
        json.call(iterable.asList(), callable)
        return json
    }

    StreamingJsonBuilder json(Object...args) {
        if(args.length == 1) {
            def val = args[0]
            if(val instanceof JsonOutput.JsonUnescaped) {
                this.json((JsonOutput.JsonUnescaped)val)
            }
            else if(val instanceof JsonOutput.JsonWritable) {
                this.json((JsonOutput.JsonWritable)val)
            }
            else {
                json.call val
            }
        }
        else {
            json.call args
        }
        return json
    }

    private GsonViewBaseTemplate prepareParentWritable() {
        parentModel.putAll(binding.variables)
        for(o in binding.variables.values()) {
            parentModel.put(GrailsNameUtils.getPropertyName(o.getClass().getSuperclass().getName()), o)
        }
        GsonViewBaseTemplate writable = (GsonViewBaseTemplate) parentTemplate.make((Map) parentModel)
        writable.inline = true
        writable.locale = locale
        writable.response = response
        writable.request = request
        writable.controllerNamespace = controllerNamespace
        writable.controllerName = controllerName
        writable.actionName = actionName
        writable.config = config
        writable.generator = generator
        return writable
    }


    private void resetProcessedObjects() {
        if (binding.hasVariable(DefaultGrailsJsonViewHelper.PROCESSED_OBJECT_VARIABLE)) {
            Map processed = (Map) binding.getVariable(DefaultGrailsJsonViewHelper.PROCESSED_OBJECT_VARIABLE)
            processed.clear()
        }
    }
}
