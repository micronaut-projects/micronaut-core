package io.micronaut.docs.http.bind.binders;
// tag:class[]
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import org.checkerframework.checker.nullness.Opt;

import javax.inject.Singleton;
import java.util.Base64;
import java.util.Optional;

@Singleton
public class MyBoundBeanTypedRequestArgumentBinder implements TypedRequestArgumentBinder<MyBoundBean> {

    @Override
    public BindingResult<MyBoundBean> bind(ArgumentConversionContext<MyBoundBean> context, HttpRequest<?> source) { //<1>
        MyBoundBean result = new MyBoundBean();
        result.setBindingType("TYPED");
        result.setShoppingCartSize(source.getCookies().get("shoppingCart", Integer.class).orElse(null));
        result.setDisplayName(source.getCookies().get("displayName").getValue());
        String userNameBase64 = source.getHeaders().getAuthorization().orElse(null);
        String userName;
        try {
             userName = new String(Base64.getDecoder().decode(userNameBase64.substring(6)))
                    .split(":", 2)[0];
        } catch (IllegalArgumentException iae) {
            context.reject(iae);
            return Optional::empty;
        }
        result.setUserName(userName);
        result.setBody(source.getBody(String.class).orElse(null));
        return () -> Optional.of(result); //<2>
    }

    @Override
    public Argument<MyBoundBean> argumentType() {
        return Argument.of(MyBoundBean.class); //<3>
    }
}
// end:class[]