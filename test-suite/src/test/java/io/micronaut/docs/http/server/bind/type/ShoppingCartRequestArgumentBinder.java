package io.micronaut.docs.http.server.bind.type;

// tag::class[]
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.jackson.serialize.JacksonObjectSerializer;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class ShoppingCartRequestArgumentBinder implements TypedRequestArgumentBinder<ShoppingCart> {

    private final JacksonObjectSerializer objectSerializer;

    public ShoppingCartRequestArgumentBinder(JacksonObjectSerializer objectSerializer) {
        this.objectSerializer = objectSerializer;
    }

    @Override
    public BindingResult<ShoppingCart> bind(ArgumentConversionContext<ShoppingCart> context, HttpRequest<?> source) { //<1>
        Cookie cookie = source.getCookies().get("shoppingCart");

        if (cookie != null) {
            return () -> objectSerializer.deserialize( //<2>
                    cookie.getValue().getBytes(),
                    ShoppingCart.class);
        }

        return Optional::empty;
    }

    @Override
    public Argument<ShoppingCart> argumentType() {
        return Argument.of(ShoppingCart.class); //<3>
    }
}
// end::class[]
