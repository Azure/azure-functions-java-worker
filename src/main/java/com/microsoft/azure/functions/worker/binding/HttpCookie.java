package com.microsoft.azure.functions.worker.binding;
import com.microsoft.azure.functions.rpc.messages.RpcHttpCookie;
public class HttpCookie {
    private String name;
    private String value;
    private String domain;
    private String path;
    private Boolean secure;
    private Boolean httpOnly;
    private String expires; // In ISO 8601 format
    private Double maxAge;
    private RpcHttpCookie.SameSite sameSite;

    // Constructor with required fields
    public HttpCookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    // Getters and setters with chainable methods
    public String getName() {
        return name;
    }

    public HttpCookie setName(String name) {
        this.name = name;
        return this;
    }

    public String getValue() {
        return value;
    }

    public HttpCookie setValue(String value) {
        this.value = value;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public HttpCookie setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getPath() {
        return path;
    }

    public HttpCookie setPath(String path) {
        this.path = path;
        return this;
    }

    public Boolean getSecure() {
        return secure;
    }

    public HttpCookie setSecure(Boolean secure) {
        this.secure = secure;
        return this;
    }

    public Boolean getHttpOnly() {
        return httpOnly;
    }

    public HttpCookie setHttpOnly(Boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public String getExpires() {
        return expires;
    }

    public HttpCookie setExpires(String expires) {
        this.expires = expires;
        return this;
    }

    public Double getMaxAge() {
        return maxAge;
    }

    public HttpCookie setMaxAge(Double maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public com.microsoft.azure.functions.rpc.messages.RpcHttpCookie.SameSite getSameSite() {
        return sameSite;
    }

    public HttpCookie setSameSite(RpcHttpCookie.SameSite sameSite) {
        this.sameSite = sameSite;
        return this;
    }
}