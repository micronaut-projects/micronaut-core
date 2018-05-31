package io.micronaut.function.aws.alexa;

import com.amazon.ask.Skill;
import com.amazon.ask.Skills;
import hello.world.lambda.java.helloworld.handlers.*;
import io.micronaut.function.FunctionBean;

@FunctionBean("hello-world")
public class HelloWorldSkillFunction extends AlexFunction {
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


