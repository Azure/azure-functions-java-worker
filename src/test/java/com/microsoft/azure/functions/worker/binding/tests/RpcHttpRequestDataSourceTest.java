package com.microsoft.azure.functions.worker.binding.tests;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.binding.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class RpcHttpRequestDataSourceTest {

  public void HttpRequestStringBody(HttpRequestMessage<String> request) {
  }

  public void HttpRequestIntBody(HttpRequestMessage<Integer> request) {
  }
  
  public void HttpRequestBinaryBody(HttpRequestMessage<byte[]> request) {
  }

  public void HttpRequestStreamBody(HttpRequestMessage<InputStream> request) {
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
    assertEquals( "testStringBody", requestMsg.getBody().toString());
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
    assertEquals(expectedString, actualString);
  }

  private Method getFunctionMethod(String methodName) {
    RpcHttpRequestDataSourceTest httpDataSourceTests = new RpcHttpRequestDataSourceTest();
    Class<? extends RpcHttpRequestDataSourceTest> httpDataSourceTestsClass = httpDataSourceTests
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

  @Test
  public void rpcHttpDataSource_To_HttpRequestMessage_StreamBody_returnsLiveStream() throws Exception {
    Method method = getFunctionMethod("HttpRequestStreamBody");
    Parameter[] parameters = method.getParameters();
    String sourceKey = "testRpcHttp";

    InputStream liveStream = new ByteArrayInputStream("live-stream-body".getBytes());

    RpcHttp input = getTestRpcHttp(new byte[0]);
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input, liveStream);
    Optional<BindingData> bindingData = rpcHttp.computeByName(sourceKey,
        parameters[0].getParameterizedType());
    BindingData arg = bindingData.orElseThrow(WrongMethodTypeException::new);
    HttpRequestMessage<?> requestMsg = (HttpRequestMessage<?>) arg.getValue();
    assertNotNull(requestMsg.getBody());
    assertSame(liveStream, requestMsg.getBody(),
        "Streaming-input path must hand back the live request body unmodified");
  }

  @Test
  public void rpcHttpDataSource_To_HttpRequestMessage_StreamBody_withoutLiveStream_fallsThrough()
      throws Exception {
    Method method = getFunctionMethod("HttpRequestStreamBody");
    Parameter[] parameters = method.getParameters();
    String sourceKey = "testRpcHttp";

    // Without a live stream the streaming branch must not trigger. No converter
    // is registered for InputStream, so the buffered pipeline raises a
    // ClassCastException, the same behavior as before streaming input existed.
    RpcHttp input = getTestRpcHttp("ignored-body".getBytes());
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input);
    assertThrows(
        ClassCastException.class,
        () -> rpcHttp.computeByName(sourceKey, parameters[0].getParameterizedType()));
  }

}
