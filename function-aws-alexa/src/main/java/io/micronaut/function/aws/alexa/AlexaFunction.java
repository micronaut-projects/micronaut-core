package io.micronaut.function.aws.alexa;

import com.amazon.ask.Skill;
import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.model.ResponseEnvelope;
import io.micronaut.function.FunctionBean;

import java.util.function.Function;

/**
 * This is the base function you extend for Alexa skills support. For now you have to override apply but just call super() in it.
 * Your skill itself goes in implementing getSkill() and adding handlers for your intents.
 */
public abstract class AlexaFunction implements Function<RequestEnvelope, ResponseEnvelope> {

    /**
     * This allows the requestEnvelope to be parsed with Jackson to invoke the ASK SDK.
     * @param requestEnvelope incoming request from Alexa service
     * @return this is the wrapped response object that is turned back into JSON
     */
    @Override
    public ResponseEnvelope apply(RequestEnvelope requestEnvelope) {
        return getSkill().invoke(requestEnvelope);
    }

    /**
     * Implement this with returning the handlers for your intents.
     * @return Skill object, wraps intents in it
     */
    protected abstract Skill getSkill();
}
