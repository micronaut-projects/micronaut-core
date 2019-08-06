package io.micronaut.docs.config.converters;

import io.micronaut.context.ApplicationContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

//tag::configSpec[]
public class MyConfigurationPropertiesSpec {

    //tag::runContext[]
    private static ApplicationContext ctx;

    @BeforeClass
    public static void setupCtx() {
        ctx = ApplicationContext.run(
                new LinkedHashMap<String, Object>() {{
                    put("myapp.updatedAt", // <1>
                            new LinkedHashMap<String, Integer>() {{
                                put("day", 28);
                                put("month", 10);
                                put("year", 1982);
                            }}
                    );
                }}
        );
    }

    @AfterClass
    public static void teardownCtx() {
        if(ctx != null) {
            ctx.stop();
        }
    }
    //end::runContext[]

    @Test
    public void testConvertDateFromMap() {
        MyConfigurationProperties props = ctx.getBean(MyConfigurationProperties.class);

        LocalDate expectedDate = LocalDate.of(1982, 10, 28);
        assertEquals(expectedDate, props.getUpdatedAt());
    }
}
//end::configSpec[]