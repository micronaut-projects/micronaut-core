package io.micronaut.function.aws.alexa

import com.amazon.ask.Skill
import com.amazon.ask.Skills
import com.amazon.ask.exception.AskSdkException
import com.amazonaws.services.lambda.runtime.Context
import hello.world.lambda.java.helloworld.handlers.CancelandStopIntentHandler
import hello.world.lambda.java.helloworld.handlers.HelloWorldIntentHandler
import hello.world.lambda.java.helloworld.handlers.HelpIntentHandler
import hello.world.lambda.java.helloworld.handlers.LaunchRequestHandler
import hello.world.lambda.java.helloworld.handlers.SessionEndedRequestHandler
import io.micronaut.function.aws.alexa.AlexaSkillHandler
import spock.lang.Specification

import java.util.function.Function


public class AlexaSkillHandlerSpec extends Specification {

    void "test micronaut alexa stream handler with simulated input"() {
        given:
        Skill helloWorldSkill = Skills.standard()
                .addRequestHandlers(
                new CancelandStopIntentHandler(),
                new HelloWorldIntentHandler(),
                new HelpIntentHandler(),
                new LaunchRequestHandler(),
                new SessionEndedRequestHandler())
                .withSkillId("amzn1.ask.skill.cbfe084d-1ec9-4b79-83e5-8544c7181b5b")
                .build();
        AlexaSkillHandler requestHandler = new AlexaSkillHandler(helloWorldSkill)


        when:
        def body = '{"title":"The Stand"}'
        def file = new File("src/test/groovy/io/micronaut/function/aws/alexa/inputEnvelope.json").newInputStream()


        def output = new ByteArrayOutputStream()
        requestHandler.handleRequest(
                file,
                output,
                Mock(Context)
        )

        then:
        assert output

        output.toString() == "{\"version\":\"1.0\",\"userAgent\":\"ask-java/2.2.1 Java/1.8.0_161\",\"response\":{\"outputSpeech\":{\"type\":\"SSML\",\"ssml\":\"<speak>Welcome to the Alexa Skills Kit, you can say hello</speak>\"},\"card\":{\"type\":\"Simple\",\"title\":\"HelloWorld\",\"content\":\"Welcome to the Alexa Skills Kit, you can say hello\"},\"reprompt\":{\"outputSpeech\":{\"type\":\"SSML\",\"ssml\":\"<speak>Welcome to the Alexa Skills Kit, you can say hello</speak>\"}},\"shouldEndSession\":false}}"
    }


    void "test micronaut alexa stream handler with simulated input - invalid skill id"() {
        given:
        Skill helloWorldSkill = Skills.standard()
                .addRequestHandlers(
                new CancelandStopIntentHandler(),
                new HelloWorldIntentHandler(),
                new HelpIntentHandler(),
                new LaunchRequestHandler(),
                new SessionEndedRequestHandler())
                .withSkillId("abc123")
                .build();
        AlexaSkillHandler requestHandler = new AlexaSkillHandler(helloWorldSkill)


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

}