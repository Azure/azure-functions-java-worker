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
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


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
  public void rpcHttpDataSource_To_HttpRequestMessage_StreamBody_returnsLiveExchangeStream() throws Exception {
    Method method = getFunctionMethod("HttpRequestStreamBody");
    Parameter[] parameters = method.getParameters();
    String sourceKey = "testRpcHttp";

    byte[] payload = "live-stream-body".getBytes();
    InputStream liveStream = new ByteArrayInputStream(payload);
    HttpExchange exchange = mock(HttpExchange.class);
    when(exchange.getRequestBody()).thenReturn(liveStream);

    RpcHttp input = getTestRpcHttp(new byte[0]);
    RpcHttpRequestDataSource.setCurrentExchange(exchange);
    try {
      RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input);
      Optional<BindingData> bindingData = rpcHttp.computeByName(sourceKey,
          parameters[0].getParameterizedType());
      BindingData arg = bindingData.orElseThrow(WrongMethodTypeException::new);
      HttpRequestMessage<?> requestMsg = (HttpRequestMessage<?>) arg.getValue();
      assertNotNull(requestMsg.getBody());
      assertSame(liveStream, requestMsg.getBody(),
          "Streaming-input path must hand back the exchange's live request body unmodified");
    } finally {
      RpcHttpRequestDataSource.setCurrentExchange(null);
    }
  }

  @Test
  public void rpcHttpDataSource_To_HttpRequestMessage_StreamBody_withNoCapturedExchange_fallsThrough()
      throws Exception {
    Method method = getFunctionMethod("HttpRequestStreamBody");
    Parameter[] parameters = method.getParameters();
    String sourceKey = "testRpcHttp";

    // No exchange installed on the ThreadLocal: the streaming-input branch
    // must not trigger and we should fall through to the existing
    // bodyDataSource path. With a byte[] body and InputStream target type, no
    // converter is registered for InputStream, so the existing pipeline raises
    // a ClassCastException -- the same behavior as before this feature was
    // added. The point of this test is to confirm we have NOT silently
    // swallowed the failure.
    RpcHttp input = getTestRpcHttp("ignored-body".getBytes());
    RpcHttpRequestDataSource.setCurrentExchange(null);
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input);
    org.junit.jupiter.api.Assertions.assertThrows(
        ClassCastException.class,
        () -> rpcHttp.computeByName(sourceKey, parameters[0].getParameterizedType()),
        "Without a captured exchange, InputStream resolution must fall through and fail as it did before the feature");
  }

  @Test
  public void setCurrentExchange_null_clearsThreadLocal() throws Exception {
    HttpExchange exchange = mock(HttpExchange.class);
    RpcHttpRequestDataSource.setCurrentExchange(exchange);
    // Cleared before construction: the data source must NOT capture the prior
    // exchange.
    RpcHttpRequestDataSource.setCurrentExchange(null);

    Method method = getFunctionMethod("HttpRequestStreamBody");
    Parameter[] parameters = method.getParameters();
    RpcHttp input = getTestRpcHttp(new byte[0]);
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource("k", input);
    // Same expectation as the no-exchange-ever case: streaming branch doesn't
    // fire, fallthrough fails. If we had leaked the previously-installed
    // exchange, mockito would have returned a real (empty default) stream and
    // the call would succeed instead of throwing.
    org.junit.jupiter.api.Assertions.assertThrows(
        ClassCastException.class,
        () -> rpcHttp.computeByName("k", parameters[0].getParameterizedType()),
        "After clearing the ThreadLocal, the prior exchange must not leak into newly-constructed data sources");
  }

}
