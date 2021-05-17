package io.micronaut.docs.ioc.validation.iterableGenericParameters;

import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

@Singleton
public class BookInfoService {
    // tag::validate-iterables[]
    public void setBookAuthors(@NotBlank String bookName,
                               List<@NotBlank String> authors // <1>
    ) {
        System.out.println("Set book authors for book " + bookName);
    }

    public void setBookSectionPages(@NotBlank String bookName,
                                    Map<@NotBlank String, @Min(1) Integer> sectionStartPages // <2>
    ) {
        System.out.println("Set the start pages for all sections of book " + bookName);
    }
    // tag::validate-iterables[]
}
