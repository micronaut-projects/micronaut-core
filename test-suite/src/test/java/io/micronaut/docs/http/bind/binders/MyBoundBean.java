package io.micronaut.docs.http.bind.binders;

public class MyBoundBean {
    private String userName;
    private String displayName;
    private Integer shoppingCartSize;
    private String bindingType;
    private String body;

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }


    public String getBindingType() {
        return bindingType;
    }

    public void setBindingType(String bindingType) {
        this.bindingType = bindingType;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getShoppingCartSize() {
        return shoppingCartSize;
    }

    public void setShoppingCartSize(Integer shoppingCartSize) {
        this.shoppingCartSize = shoppingCartSize;
    }
}
