package com.microsoft.azure.functions.worker.binding.tests;


import java.lang.invoke.WrongMethodTypeException;
import java.util.Optional;

import org.apache.commons.lang3.ArrayUtils;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.RpcByteArrayDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RpcByteArrayDataSourceTest {

  @Test
  public void rpcByteArrayDataSource_To_byteArray() {
    String sourceKey = "testByteArray";
    String expectedString = "Example String";
    byte[] inputBytes = expectedString.getBytes();
    ByteString inputByteString = ByteString.copyFrom(inputBytes);
    RpcByteArrayDataSource stringData = new RpcByteArrayDataSource(sourceKey, inputByteString);

    Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, byte[].class);
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    byte[] actualBytes = (byte[]) actualArg.getValue();
    String actualString = new String(actualBytes);
    assertEquals(actualString, expectedString);
  }

  @Test
  public void rpcByteArrayDataSource_To_ByteArray() {
    String sourceKey = "testByteArray";
    String expectedString = "Example String";
    byte[] inputBytes = expectedString.getBytes();
    ByteString inputByteString = ByteString.copyFrom(inputBytes);
    RpcByteArrayDataSource stringData = new RpcByteArrayDataSource(sourceKey, inputByteString);

    Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, Byte[].class);
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    Byte[] actualBytes = (Byte[]) actualArg.getValue();
    String actualString = new String(ArrayUtils.toPrimitive(actualBytes));
    assertEquals(actualString, expectedString);
  }

  @Test
  public void rpcByteArrayDataSource_To_POJO() {
    String sourceKey = "testByteArray";
    String expectedString = "{\"blobText\":\"Example String\"}";
    TestBlobData testBlobData = new TestBlobData();
    testBlobData.blobText = "Example String";
    byte[] inputBytes = expectedString.getBytes();
    ByteString inputByteString = ByteString.copyFrom(inputBytes);
    RpcByteArrayDataSource stringData = new RpcByteArrayDataSource(sourceKey, inputByteString);

    Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey,
        TestBlobData.class);
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    TestBlobData actualBlobData = (TestBlobData) actualArg.getValue();
    assertEquals(actualBlobData.blobText, testBlobData.blobText);
  }
  
  @Test
  public void rpcByteArrayDataSource_To_String() {
    String sourceKey = "testByteArray";
    String expectedString = "Example String";    
    byte[] inputBytes = expectedString.getBytes();
    ByteString inputByteString = ByteString.copyFrom(inputBytes);
    RpcByteArrayDataSource stringData = new RpcByteArrayDataSource(sourceKey, inputByteString);

    Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey,
        String.class);
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    String actualBlobData = (String) actualArg.getValue();
    assertEquals(actualBlobData, expectedString);
  }

  public static class TestBlobData {
    public String blobText;
  }
}
