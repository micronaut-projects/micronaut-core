package io.micronaut.function.aws.alexa;

import com.amazon.ask.Skill;
import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.model.ResponseEnvelope;

import java.util.function.Function;

public abstract class AlexFunction implements Function<RequestEnvelope, ResponseEnvelope> {

    @Override
    public final ResponseEnvelope apply(RequestEnvelope requestEnvelope) {
        return getSkill().invoke(requestEnvelope);
    }

    protected abstract Skill getSkill();
}
