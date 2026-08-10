package com.example.gatewayservice.model;

public class GatewayResponse<T> {

    private int code;
    private String message;
    private T data;

    public GatewayResponse() {
    }

    public GatewayResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> GatewayResponse<T> ok(T data) {
        return new GatewayResponse<>(0, "OK", data);
    }

    public static <T> GatewayResponse<T> fail(int code, String message) {
        return new GatewayResponse<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}

