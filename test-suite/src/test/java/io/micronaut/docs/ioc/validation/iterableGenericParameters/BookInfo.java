package io.micronaut.docs.ioc.validation.iterableGenericParameters;

import java.util.List;
import java.util.Map;

// tag::object[]

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class BookInfo {
    private List<@NotBlank String> authors; // <1>

    private Map<@NotBlank String, @Min(1) Integer> sectionStartPages; // <2>
}

// end:object[]
