package com.microsoft.azure.functions.worker.binding.tests;


import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

import com.microsoft.azure.functions.rpc.messages.NullableTypes.NullableString;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.handler.FunctionEnvironmentReloadRequestHandler;
import com.microsoft.azure.functions.worker.reflect.DefaultClassLoaderProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RpcHttpRequestDataSourceTest {

  public void HttpRequestStringBody(HttpRequestMessage<String> request) {
  }

  public void HttpRequestIntBody(HttpRequestMessage<Integer> request) {
  }
  
  public void HttpRequestBinaryBody(HttpRequestMessage<byte[]> request) {
  }

  public static RpcHttp getTestRpcHttp(
          Object inputBody,
          Map<String, String> headersMap,
          Map<String, String> queryMap) throws Exception {
    TypedData.Builder dataBuilder = TypedData.newBuilder();
    RpcHttp.Builder httpBuilder = RpcHttp.newBuilder()
        .setStatusCode(Integer.toString(HttpStatus.OK.value()));
    if (headersMap != null) {
      for (String key : headersMap.keySet()) {
        httpBuilder.putNullableHeaders(key, NullableString.newBuilder().setValue(headersMap.get(key)).build());
      }
    }
    if (queryMap != null) {
      for (String key : queryMap.keySet()) {
        httpBuilder.putNullableQuery(key, NullableString.newBuilder().setValue(queryMap.get(key)).build());
      }
    }
    RpcUnspecifiedDataTarget bodyTarget = new RpcUnspecifiedDataTarget();
    Object body = inputBody;
    bodyTarget.setValue(body);
    bodyTarget.computeFromValue().ifPresent(httpBuilder::setBody);
    dataBuilder.setHttp(httpBuilder);
    return httpBuilder.build();
  }

  @Test
  public void rpcHttpDataSourceToHttpRequestMessageEnvSettingEnabled() throws Exception {
    DefaultClassLoaderProvider classLoader = new DefaultClassLoaderProvider();
    JavaFunctionBroker broker = new JavaFunctionBroker(classLoader);
    FunctionEnvironmentReloadRequestHandler envHandler = new FunctionEnvironmentReloadRequestHandler(broker);
    Map<String, String> existingVariables = System.getenv();
    Map<String, String> newEnvVariables = new HashMap<>();
    newEnvVariables.putAll(existingVariables);
    newEnvVariables.put("FUNCTIONS_WORKER_NULLABLE_VALUES_ENABLED", "true");
    envHandler.setEnv(newEnvVariables);
    Method httpRequestMessageStringBodyMethod = getFunctionMethod("HttpRequestStringBody");
    Map<String, String> queryMap = new HashMap<String, String>() {{
      put("name", "");
      put("count", "1");
    }};
    Map<String, String> headersMap = new HashMap<String, String>() {{
      put("cookie", "");
      put("accept-encoding", "gzip, deflate, br");
    }};
    Parameter[] parameters = httpRequestMessageStringBodyMethod.getParameters();
    String sourceKey = "testRpcHttp";
    RpcHttp input = getTestRpcHttp(null, headersMap, queryMap);
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input);
    Optional<BindingData> actualBindingData = rpcHttp.computeByName(sourceKey,
            parameters[0].getParameterizedType());
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    HttpRequestMessage<?> requestMsg = (HttpRequestMessage<?>) actualArg.getValue();

    assertTrue(requestMsg.getQueryParameters().get("name").isEmpty());
    assertEquals("1", requestMsg.getQueryParameters().get("count"));
    assertTrue(requestMsg.getHeaders().get("cookie").isEmpty());
    assertEquals("gzip, deflate, br", requestMsg.getHeaders().get("accept-encoding"));
  }

  @Test
  public void rpcHttpDataSourceToHttpRequestMessageEnvSettingDisabled() throws Exception {
    DefaultClassLoaderProvider classLoader = new DefaultClassLoaderProvider();
    JavaFunctionBroker broker = new JavaFunctionBroker(classLoader);
    FunctionEnvironmentReloadRequestHandler envHandler = new FunctionEnvironmentReloadRequestHandler(broker);
    Map<String, String> existingVariables = System.getenv();
    Map<String, String> newEnvVariables = new HashMap<>();
    newEnvVariables.putAll(existingVariables);
    newEnvVariables.put("FUNCTIONS_WORKER_NULLABLE_VALUES_ENABLED", "false");
    envHandler.setEnv(newEnvVariables);
    Method httpRequestMessageStringBodyMethod = getFunctionMethod("HttpRequestStringBody");
    Map<String, String> queryMap = new HashMap<String, String>() {{
      put("name", "");
      put("count", "1");
    }};
    Map<String, String> headersMap = new HashMap<String, String>() {{
      put("cookie", "");
      put("accept-encoding", "gzip, deflate, br");
    }};
    Parameter[] parameters = httpRequestMessageStringBodyMethod.getParameters();
    String sourceKey = "testRpcHttp";
    RpcHttp input = getTestRpcHttp(null, headersMap, queryMap);
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input);
    Optional<BindingData> actualBindingData = rpcHttp.computeByName(sourceKey,
            parameters[0].getParameterizedType());
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    HttpRequestMessage<?> requestMsg = (HttpRequestMessage<?>) actualArg.getValue();

    assertNull(requestMsg.getQueryParameters().get("name"));
    assertEquals("1", requestMsg.getQueryParameters().get("count"));
    assertNull(requestMsg.getHeaders().get("cookie"));
    assertEquals("gzip, deflate, br", requestMsg.getHeaders().get("accept-encoding"));
  }

  @Test
  public void rpcHttpDataSourceToHttpRequestMessageStringBody() throws Exception {

    Method httpRequestMessageStringBodyMethod = getFunctionMethod("HttpRequestStringBody");

    Parameter[] parameters = httpRequestMessageStringBodyMethod.getParameters();
    String sourceKey = "testRpcHttp";
    RpcHttp input = getTestRpcHttp("testStringBody", null, null);
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input);
    Optional<BindingData> actualBindingData = rpcHttp.computeByName(sourceKey,
        parameters[0].getParameterizedType());
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    HttpRequestMessage<?> requestMsg = (HttpRequestMessage<?>) actualArg.getValue();
    assertEquals( "testStringBody", requestMsg.getBody().toString());
  }

  @Test
  public void rpcHttpDataSourceToHttpRequestMessageIntegerBody() throws Exception {

    Method httpRequestMessageStringBodyMethod = getFunctionMethod("HttpRequestIntBody");

    Parameter[] parameters = httpRequestMessageStringBodyMethod.getParameters();
    String sourceKey = "testRpcHttp";
    RpcHttp input = getTestRpcHttp(1234, null, null);
    RpcHttpRequestDataSource rpcHttp = new RpcHttpRequestDataSource(sourceKey, input);
    Optional<BindingData> actualBindingData = rpcHttp.computeByName(sourceKey,
        parameters[0].getParameterizedType());
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    HttpRequestMessage<?> requestMsg = (HttpRequestMessage<?>) actualArg.getValue();
    assertEquals(1234, Integer.parseInt(requestMsg.getBody().toString()));
  }

  @Test
  public void rpcHttpDataSourceToHttpRequestMessageByteArrayBody() throws Exception {

    Method httpRequestMessageStringBodyMethod = getFunctionMethod("HttpRequestBinaryBody");

    Parameter[] parameters = httpRequestMessageStringBodyMethod.getParameters();
    String sourceKey = "testRpcHttp";
    String expectedString = "Example String";
    byte[] inputBytes = expectedString.getBytes();
    RpcHttp input = getTestRpcHttp(inputBytes, null, null);
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

}
