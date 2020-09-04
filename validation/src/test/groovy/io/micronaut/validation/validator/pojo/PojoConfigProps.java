package io.micronaut.validation.validator.pojo;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.validation.Pojo;

import javax.validation.Valid;
import java.util.List;

@ConfigurationProperties("test.valid")
public class PojoConfigProps {

    @Valid
    private List<Pojo> pojos;

    public List<Pojo> getPojos() {
        return pojos;
    }

    public void setPojos(List<Pojo> pojos) {
        this.pojos = pojos;
    }

}
