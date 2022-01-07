package com.microsoft.azure.functions.worker.binding.tests;

import static org.junit.Assert.assertEquals;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import org.junit.Test;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.binding.*;

public class RpcHttpRequestDataSourceTests {

  public void HttpRequestStringBody(HttpRequestMessage<String> request) {
  }

  public void HttpRequestIntBody(HttpRequestMessage<Integer> request) {
  }
  
  public void HttpRequestBinaryBody(HttpRequestMessage<byte[]> request) {
  }

  public static RpcHttp getTestRpcHttp(Object inputBody) throws Exception {
    TypedData.Builder dataBuilder = TypedData.newBuilder();
    RpcHttp.Builder httpBuilder = RpcHttp.newBuilder()
        .setStatusCode(Integer.toString(HttpStatus.OK.value()));
    Map<String, String> headers = new HashMap<>();
    headers.put("header", "testHeader");
    headers.forEach(httpBuilder::putHeaders);
    RpcUnspecifiedDataTarget bodyTarget = new RpcUnspecifiedDataTarget();
    Object body = inputBody;
    bodyTarget.setValue(body);
    bodyTarget.computeFromValue().ifPresent(httpBuilder::setBody);
    dataBuilder.setHttp(httpBuilder);
    return httpBuilder.build();
  }

  @Test
  public void rpcHttpDataSource_To_HttpRequestMessage_StringBody() throws Exception {

    Method httpRequestMessageStringBodyMethod = getFunctionMethod("HttpRequestStringBody");

    Parameter[] parameters = httpRequestMessageStringBodyMethod.getParameters();
    String sourceKey = "testRpcHttp";
    RpcHttp input = getTestRpcHttp("testStringBody");
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input);
    Optional<BindingData> actualBindingData = rpcHttp.computeByName(sourceKey,
        parameters[0].getParameterizedType());
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    HttpRequestMessage<?> requestMsg = (HttpRequestMessage<?>) actualArg.getValue();
    assertEquals(requestMsg.getBody().toString(), "testStringBody");
  }

  @Test
  public void rpcHttpDataSource_To_HttpRequestMessage_IntegerBody() throws Exception {

    Method httpRequestMessageStringBodyMethod = getFunctionMethod("HttpRequestIntBody");

    Parameter[] parameters = httpRequestMessageStringBodyMethod.getParameters();
    String sourceKey = "testRpcHttp";
    RpcHttp input = getTestRpcHttp(1234);
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input);
    Optional<BindingData> actualBindingData = rpcHttp.computeByName(sourceKey,
        parameters[0].getParameterizedType());
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    HttpRequestMessage<?> requestMsg = (HttpRequestMessage<?>) actualArg.getValue();
    assertEquals(1234, Integer.parseInt(requestMsg.getBody().toString()));
  }

  @Test
  public void rpcHttpDataSource_To_HttpRequestMessage_byteArrayBody() throws Exception {

    Method httpRequestMessageStringBodyMethod = getFunctionMethod("HttpRequestBinaryBody");

    Parameter[] parameters = httpRequestMessageStringBodyMethod.getParameters();
    String sourceKey = "testRpcHttp";
    String expectedString = "Example String";
    byte[] inputBytes = expectedString.getBytes();
    RpcHttp input = getTestRpcHttp(inputBytes);
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input);
    Optional<BindingData> actualBindingData = rpcHttp.computeByName(sourceKey,
        parameters[0].getParameterizedType());
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    HttpRequestMessage<?> requestMsg = (HttpRequestMessage<?>) actualArg.getValue();
    byte[] actualBytes = (byte[])requestMsg.getBody();
    String actualString = new String (actualBytes);
    assertEquals(actualString, expectedString);    
  }

  private Method getFunctionMethod(String methodName) {
    RpcHttpRequestDataSourceTests httpDataSourceTests = new RpcHttpRequestDataSourceTests();
    Class<? extends RpcHttpRequestDataSourceTests> httpDataSourceTestsClass = httpDataSourceTests
        .getClass();
    Method[] methods = httpDataSourceTestsClass.getMethods();
    Method functionMethod = null;
    for (Method method : methods) {
      if (method.getName() == methodName) {
        functionMethod = method;
        break;
      }
    }
    return functionMethod;
  }

}
