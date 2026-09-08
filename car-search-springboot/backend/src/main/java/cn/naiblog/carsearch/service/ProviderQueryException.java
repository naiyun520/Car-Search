package cn.naiblog.carsearch.service;
public class ProviderQueryException extends RuntimeException {
    private final String providerCode, requestId;
    public ProviderQueryException(String message,String providerCode,String requestId){super(message);this.providerCode=providerCode;this.requestId=requestId;}
    public String providerCode(){return providerCode;} public String requestId(){return requestId;} public String providerRequestId(){return requestId;}
}
