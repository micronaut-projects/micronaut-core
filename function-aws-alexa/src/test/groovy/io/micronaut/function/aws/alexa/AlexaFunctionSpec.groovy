package io.micronaut.function.aws.alexa

import com.amazon.ask.Skill
import com.amazon.ask.Skills
import com.amazon.ask.exception.AskSdkException
import com.amazon.ask.model.RequestEnvelope
import com.amazon.ask.model.ResponseEnvelope
import com.amazonaws.services.lambda.runtime.Context
import io.micronaut.context.env.Environment
import io.micronaut.function.FunctionBean
import io.micronaut.function.aws.MicronautRequestStreamHandler
import io.micronaut.function.aws.alexa.handlers.CancelandStopIntentHandler
import io.micronaut.function.aws.alexa.handlers.HelloWorldIntentHandler
import io.micronaut.function.aws.alexa.handlers.HelpIntentHandler
import io.micronaut.function.aws.alexa.handlers.LaunchRequestHandler
import io.micronaut.function.aws.alexa.handlers.SessionEndedRequestHandler
import spock.lang.Specification

public class AlexaFunctionSpec extends Specification {

    void "test micronaut alexa stream handler with simulated input"() {
        given:

        MicronautRequestStreamHandler requestHandler = new MicronautRequestStreamHandler(){
            @Override
            protected String resolveFunctionName(Environment env) {
                "hello-world"
            }
        }


        when:
        def file = new File("src/test/groovy/io/micronaut/function/aws/alexa/inputEnvelope.json").newInputStream()


        def output = new ByteArrayOutputStream()
        requestHandler.handleRequest(
                file,
                output,
                Mock(Context)
        )

        then:
        assert output

        // strip out of the jdk agent stuff since that varies per machine
        output.toString().substring(62,output.toString().length()) == "response\":{\"outputSpeech\":{\"type\":\"SSML\",\"ssml\":\"<speak>Welcome to the Alexa Skills Kit, you can say hello</speak>\"},\"card\":{\"type\":\"Simple\",\"title\":\"HelloWorld\",\"content\":\"Welcome to the Alexa Skills Kit, you can say hello\"},\"reprompt\":{\"outputSpeech\":{\"type\":\"SSML\",\"ssml\":\"<speak>Welcome to the Alexa Skills Kit, you can say hello</speak>\"}},\"shouldEndSession\":false}}"
    }


    void "test micronaut alexa stream handler with simulated input - invalid skill id"() {
        given:
        MicronautRequestStreamHandler requestHandler = new MicronautRequestStreamHandler(){
            @Override
            protected String resolveFunctionName(Environment env) {
                "hello-world-invalid"
            }

        }




        when:

        def file = new File("src/test/groovy/io/micronaut/function/aws/alexa/inputEnvelope.json").newInputStream()


        def output = new ByteArrayOutputStream()
        requestHandler.handleRequest(
                file,
                output,
                Mock(Context)
        )

        then:
        AskSdkException askSdkException = thrown()
        assert askSdkException.message == "Skill ID verification failed."


    }


    @FunctionBean("hello-world")
    static class HelloWorldSkillFunction extends AlexaFunction {

        @Override
        ResponseEnvelope apply(RequestEnvelope requestEnvelope) {
            return super.apply(requestEnvelope)
        }

        @Override
        protected Skill getSkill() {
            return Skills.standard()
                    .addRequestHandlers(
                    new CancelandStopIntentHandler(),
                    new HelloWorldIntentHandler(),
                    new HelpIntentHandler(),
                    new LaunchRequestHandler(),
                    new SessionEndedRequestHandler())
                    .withSkillId("amzn1.ask.skill.cbfe084d-1ec9-4b79-83e5-8544c7181b5b")
                    .build();
        }
    }

    @FunctionBean("hello-world-invalid")
    static class HelloWorldInvalidSkillFunction extends AlexaFunction {

        @Override
        ResponseEnvelope apply(RequestEnvelope requestEnvelope) {
            return super.apply(requestEnvelope)
        }

        @Override
        protected Skill getSkill() {
            return Skills.standard()
                    .addRequestHandlers(
                    new CancelandStopIntentHandler(),
                    new HelloWorldIntentHandler(),
                    new HelpIntentHandler(),
                    new LaunchRequestHandler(),
                    new SessionEndedRequestHandler())
                    .withSkillId("abc123")
                    .build();
        }
    }

}


