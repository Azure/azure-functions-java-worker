package com.microsoft.azure.functions.worker.binding.tests;

import static org.junit.Assert.assertEquals;

import java.lang.invoke.WrongMethodTypeException;
import java.util.Optional;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.RpcByteArrayDataSource;

public class RpcByteArrayDataSourceTests {

  @Test
  public void rpcByteArrayDataSource_To_byteArray() {
    String sourceKey = "testByteArray";
    String expectedString = "Example String";
    byte[] inputBytes = expectedString.getBytes();   
    ByteString inputByteString = ByteString.copyFrom(inputBytes);
    RpcByteArrayDataSource stringData = new RpcByteArrayDataSource(sourceKey, inputByteString);
    

    Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, byte[].class);
    BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
    byte[] actualBytes = (byte[])actualArg.getValue();
    String actualString = new String (actualBytes);
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
    Byte[] actualBytes = (Byte[])actualArg.getValue();
    String actualString = new String (ArrayUtils.toPrimitive(actualBytes));
    assertEquals(actualString, expectedString);
  }
}
