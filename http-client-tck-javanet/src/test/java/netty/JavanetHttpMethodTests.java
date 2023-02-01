package netty;

import io.micronaut.tck.http.client.AuthTest;
import io.micronaut.tck.http.client.CookieTest;
import io.micronaut.tck.http.client.HttpMethodDeleteTest;
import io.micronaut.tck.http.client.HttpMethodPostTest;
import io.micronaut.tck.http.client.RedirectTest;
import io.micronaut.tck.http.client.StatusTest;

public class JavanetHttpMethodTests implements
    AuthTest
    , HttpMethodDeleteTest
    , RedirectTest
    , HttpMethodPostTest
    , StatusTest
    , CookieTest
{
}
