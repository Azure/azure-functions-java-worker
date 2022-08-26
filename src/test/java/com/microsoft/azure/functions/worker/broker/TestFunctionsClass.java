package com.microsoft.azure.functions.worker.broker;

import java.lang.*;
import java.util.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

/**
 * Azure Functions with HTTP Trigger.
 * This is the class file in TestFunctionsClass.jar file which is used in unit test JavaMethodExecutorTest
 */
public class TestFunctionsClass
{
	
	public HttpResponseMessage TestHttpTrigger(
		        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
		        final ExecutionContext context
		    ) {
		        return request.createResponseBuilder(HttpStatus.OK).build();		        
		    }
	
	public HttpResponseMessage TestHttpTriggerWithOutAnnotation(
		        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
		        final ExecutionContext context
		    ) {
		        return request.createResponseBuilder(HttpStatus.OK).build();		        
		    }
	
	public String TestHttpTriggerOverload(
		        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) String inputRequest,
		        final ExecutionContext context
		    ) {
		        return "Hello";		        
		    }
	
	public HttpResponseMessage TestHttpTriggerOverload(
		        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
		        final ExecutionContext context
		    ) {
		        return request.createResponseBuilder(HttpStatus.OK).build();		        
		    }
}
