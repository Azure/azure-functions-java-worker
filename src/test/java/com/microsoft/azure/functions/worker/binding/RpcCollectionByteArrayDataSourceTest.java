package com.microsoft.azure.functions.worker.binding;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.RpcCollectionByteArrayDataSource;
import com.microsoft.azure.functions.rpc.messages.CollectionBytes;
import com.microsoft.azure.functions.rpc.messages.CollectionBytes.Builder;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.junit.Test;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class RpcCollectionByteArrayDataSourceTest {
    @Test
    public void rpcByteArrayDataSource_To_byte_Array() {
        String sourceKey = "sourceKey";
        String expectedString = "Example String";
        byte[] expctedStingBytes = expectedString.getBytes();
        ByteString inputByteString = ByteString.copyFrom(expctedStingBytes);

        List<ByteString> input = new ArrayList<ByteString>();
        input.add(inputByteString);

        Builder a = CollectionBytes.newBuilder();
        a.addAllBytes(input);

        CollectionBytes typedDataCollectionBytes = a.build();

        RpcCollectionByteArrayDataSource stringData = new RpcCollectionByteArrayDataSource(sourceKey, typedDataCollectionBytes);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, byte[][].class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        byte[][] actualBytes = (byte[][]) actualArg.getValue();
        String actualString = new String(actualBytes[0]);
        assertEquals(actualString, expectedString);
    }

    @Test
    public void rpcByteArrayDataSource_To_Byte_Array() {
        String sourceKey = "sourceKey";
        String expectedString = "Example String";
        byte[] expctedStingBytes = expectedString.getBytes();
        ByteString inputByteString = ByteString.copyFrom(expctedStingBytes);

        List<ByteString> input = new ArrayList<ByteString>();
        input.add(inputByteString);

        Builder a = CollectionBytes.newBuilder();
        a.addAllBytes(input);

        CollectionBytes typedDataCollectionBytes = a.build();

        RpcCollectionByteArrayDataSource stringData = new RpcCollectionByteArrayDataSource(sourceKey, typedDataCollectionBytes);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, Byte[][].class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        Byte[][] actualBytes = (Byte[][]) actualArg.getValue();
        String actualString = new String(ArrayUtils.toPrimitive(actualBytes[0]));
        assertEquals(actualString, expectedString);
    }

    @Test
    public void rpcByteArrayDataSource_default_To_List_byte() {
        String sourceKey = "sourceKey";
        String expectedString = "Example String";
        byte[] expctedStingBytes = expectedString.getBytes();
        ByteString inputByteString = ByteString.copyFrom(expctedStingBytes);

        List<ByteString> input = new ArrayList<ByteString>();
        input.add(inputByteString);

        Builder a = CollectionBytes.newBuilder();
        a.addAllBytes(input);

        CollectionBytes typedDataCollectionBytes = a.build();

        RpcCollectionByteArrayDataSource stringData = new RpcCollectionByteArrayDataSource(sourceKey, typedDataCollectionBytes);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, String.class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<byte[]> actualBytes = (List) actualArg.getValue();
        String actualString = new String(actualBytes.get(0));
        assertEquals(actualString, expectedString);
    }

    @Test
    public void rpcByteArrayDataSource_To_List_byte() {
        String sourceKey = "sourceKey";
        String expectedString = "Example String";
        byte[] expctedStingBytes = expectedString.getBytes();
        ByteString inputByteString = ByteString.copyFrom(expctedStingBytes);

        List<ByteString> input = new ArrayList<ByteString>();
        input.add(inputByteString);

        Builder a = CollectionBytes.newBuilder();
        a.addAllBytes(input);

        CollectionBytes typedDataCollectionBytes = a.build();

        RpcCollectionByteArrayDataSource stringData = new RpcCollectionByteArrayDataSource(sourceKey, typedDataCollectionBytes);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, TypeUtils.parameterize(List.class, byte[].class));
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<byte[]> actualBytes = (List) actualArg.getValue();
        String actualString = new String(actualBytes.get(0));
        assertEquals(actualString, expectedString);
    }

    @Test
    public void rpcByteArrayDataSource_To_List_Byte() {
        String sourceKey = "sourceKey";
        String expectedString = "Example String";
        byte[] expctedStingBytes = expectedString.getBytes();
        ByteString inputByteString = ByteString.copyFrom(expctedStingBytes);

        List<ByteString> input = new ArrayList<ByteString>();
        input.add(inputByteString);

        Builder a = CollectionBytes.newBuilder();
        a.addAllBytes(input);

        CollectionBytes typedDataCollectionBytes = a.build();

        RpcCollectionByteArrayDataSource stringData = new RpcCollectionByteArrayDataSource(sourceKey, typedDataCollectionBytes);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, TypeUtils.parameterize(List.class, Byte[].class));
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<Byte[]> actualBytes = (List) actualArg.getValue();
        String actualString = new String(ArrayUtils.toPrimitive(actualBytes.get(0)));
        assertEquals(actualString, expectedString);
    }

    @Test
    public void rpcByteArrayDataSource_No_Generic_To_List_byte() {
        String sourceKey = "sourceKey";
        String expectedString = "Example String";
        byte[] expctedStingBytes = expectedString.getBytes();
        ByteString inputByteString = ByteString.copyFrom(expctedStingBytes);

        List<ByteString> input = new ArrayList<ByteString>();
        input.add(inputByteString);

        Builder a = CollectionBytes.newBuilder();
        a.addAllBytes(input);

        CollectionBytes typedDataCollectionBytes = a.build();

        RpcCollectionByteArrayDataSource stringData = new RpcCollectionByteArrayDataSource(sourceKey, typedDataCollectionBytes);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, List.class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<byte[]> actualBytes = (List) actualArg.getValue();
        String actualString = new String(actualBytes.get(0));
        assertEquals(actualString, expectedString);
    }




}
