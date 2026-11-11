package example.micronaut;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/compression")
public class CompressionController {

    /**
     * Longer than the default compression threshold of 1024 bytes.
     */
    static final String BODY = "Hello World ".repeat(500);

    @Get(produces = MediaType.TEXT_PLAIN)
    public String index() {
        return BODY;
    }
}
