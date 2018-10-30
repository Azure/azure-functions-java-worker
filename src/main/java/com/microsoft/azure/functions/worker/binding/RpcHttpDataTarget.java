package com.microsoft.azure.functions.worker.binding;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.TypedData;

final class RpcHttpDataTarget extends DataTarget implements HttpResponseMessage, HttpResponseMessage.Builder {
    RpcHttpDataTarget() {
        super(HTTP_TARGET_OPERATIONS);
        this.headers = new HashMap<>();
        this.httpStatus = HttpStatus.OK;
        this.httpStatusCode = HttpStatus.OK.value();
        super.setValue(this);
    }
    
    @Override
	public HttpStatusType getStatus() {	return httpStatus; }
    @Override    	  
    public int getStatusCode() { return httpStatusCode; }
    @Override
    public String getHeader(String key) { return this.headers.get(key); }
    @Override
    public Object getBody() { return this.body; }

    private int httpStatusCode;
    private HttpStatusType httpStatus;
    private Object body;
    private Map<String, String> headers;

    public static TypedData.Builder toRpcHttpData(RpcHttpDataTarget response) throws Exception {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (response != null) {
        	RpcHttp.Builder httpBuilder = RpcHttp.newBuilder().setStatusCode(Integer.toString(response.getStatusCode()));
            response.headers.forEach(httpBuilder::putHeaders);
            RpcUnspecifiedDataTarget bodyTarget = new RpcUnspecifiedDataTarget();
            bodyTarget.setValue(response.getBody());
            bodyTarget.computeFromValue().ifPresent(httpBuilder::setBody);
            dataBuilder.setHttp(httpBuilder);
        }
        return dataBuilder;
    }

    private static final DataOperations<Object, TypedData.Builder> HTTP_TARGET_OPERATIONS = new DataOperations<>();
    static {
        HTTP_TARGET_OPERATIONS.addTargetOperation(HttpResponseMessage.class, v -> toRpcHttpData((RpcHttpDataTarget) v));
        HTTP_TARGET_OPERATIONS.addTargetOperation(RpcHttpDataTarget.class, v -> toRpcHttpData((RpcHttpDataTarget) v));
    }

	
	public Builder status(HttpStatus status) {
		this.httpStatusCode = status.value();
		this.httpStatus = status;
		return this;
	}
	

	@Override
	public Builder status(HttpStatusType httpStatusType) {
		this.httpStatusCode = httpStatusType.value();
		this.httpStatus = httpStatusType;
		return this;
	}
	
	
    public Builder status(int httpStatusCode) {
        if (httpStatusCode < 100 || httpStatusCode > 599) {
            throw new IllegalArgumentException("Invalid HTTP Status code class. Valid classes are in the range of 1xx, 2xx, 3xx, 4xx and 5xx.");
        }
        this.httpStatusCode = httpStatusCode;
        this.httpStatus = HttpStatusType.custom(httpStatusCode);        
        return this;
    }


	@Override
	public Builder header(String key, String value) {
        this.headers.put(key, value);
		return this;
	}

	@Override
	public Builder body(Object body) {
        this.body = body;
		return this;
	}

	@Override
	public HttpResponseMessage build() {
		return this;
	}
}