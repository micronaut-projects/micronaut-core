package io.micronaut.function.aws.alexa;

import com.amazon.ask.Skill;
import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.model.ResponseEnvelope;
import com.amazon.ask.model.services.Serializer;
import com.amazon.ask.util.JacksonSerializer;
import com.amazonaws.services.lambda.runtime.Context;
import io.micronaut.function.aws.MicronautRequestHandler;
import io.micronaut.function.aws.MicronautRequestStreamHandler;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Alexa helper class for Micronaut. Simply extend and implement getSkill().
 * @author Ryan Vanderwerf
 */
public abstract class AlexaSkillHandler extends MicronautRequestStreamHandler {

    Serializer serializer;
    Skill skill;

    static final Logger logger = LogManager.getLogger(AlexaSkillHandler.class);

    /**
     * Constructor for the handler. Sets up serializer.
     */
    public AlexaSkillHandler() {
        this.serializer = new JacksonSerializer();
    }

    /**
     * This is the main entry point to the lambda function that we shouldn't need to mess with by extending this class.
     * The input is the RequestEnvelope (JSON) received from Alexa service
     * The output is theh ResponseEnvelope (JSON) send back to Alexa service
     */
    @Override
    public final void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        RequestEnvelope requestEnvelope = serializer.deserialize(input, RequestEnvelope.class);
        ResponseEnvelope response = getSkill().invoke(requestEnvelope);
        serializer.serialize(response, output);
    }

    /**
     * Build your skill here, it will be used when the lambda is called.
     * @return Skill from skill builder
     */
    public abstract Skill getSkill();

}