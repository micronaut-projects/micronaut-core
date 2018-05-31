package io.micronaut.jackson.serialize;

import com.amazon.ask.model.services.Serializer;
import com.amazon.ask.util.JacksonSerializer;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;

import javax.inject.Singleton;
import java.util.ArrayList;

@Singleton
public class AlexaJacksonModule extends SimpleModule {

    Serializer askSerializer = new JacksonSerializer();


/*    static final String ALEXA_MODULE = "alexa";
    static final Version VERSION = new Version(1,0,0,null,null,null);
    @Override
    public String getModuleName() {
        return ALEXA_MODULE;
    }

    @Override
    public Version version() {
        return VERSION;
    }

    @Override
    public void setupModule(SetupContext context) {
        // we need to register custom serialization for alexa classes like RequestEnvelope and ResponseEnvelope
        System.out.println("Adding Alexa Module for jackson serialization");
        Serializer askSerializer = new JacksonSerializer();
        ArrayList<JsonSerializer<?>> serializers = new ArrayList<JsonSerializer<?>>();
        serializers.add((JsonSerializer<?>) askSerializer);
        context.addSerializers(new SimpleSerializers(serializers));
        System.out.println("End Adding Alexa Module for jackson serialization");




    }*/

    public AlexaJacksonModule() {
        System.out.println("Adding Alexa Module for jackson serialization");
//        Serializer askSerializer = new JacksonSerializer();
 //       ArrayList<JsonSerializer<?>> serializers = new ArrayList<JsonSerializer<?>>();
 //       serializers.add((JsonSerializer<?>) askSerializer);
 //       setSerializers(new SimpleSerializers(serializers));
    }







}
