package com.microsoft.azure.functions.worker.binding.tests;

import com.microsoft.azure.functions.rpc.messages.TypedDataCollectionString.Builder;
import com.microsoft.azure.functions.rpc.messages.TypedDataCollectionString;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.RpcCollectionStringDataSource;
import org.junit.Test;

import java.lang.invoke.WrongMethodTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class RpcCollectionStringDataSourceTest {
    @Test
    public void rpcStringCollectionDataSource_To_string_Array() {
        String sourceKey = "sourceKey";
        String expectedString = "Example String";

        List<String> input = new ArrayList<String>();
        input.add(expectedString);

        Builder a = TypedDataCollectionString.newBuilder();
        a.addAllString(input);

        TypedDataCollectionString typedDataCollectionString = a.build();

        RpcCollectionStringDataSource stringData = new RpcCollectionStringDataSource(sourceKey, typedDataCollectionString);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, String[].class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        String[] actualStringArray = (String[]) actualArg.getValue();
        String actualString = actualStringArray[0];
        assertEquals(actualString, expectedString);
    }

    @Test
    public void rpcStringCollectionDataSource_default_To_List_string() {
        String sourceKey = "sourceKey";
        String expectedString = "Example String";

        List<String> input = new ArrayList<String>();
        input.add(expectedString);

        Builder a = TypedDataCollectionString.newBuilder();
        a.addAllString(input);

        TypedDataCollectionString typedDataCollectionString = a.build();

        RpcCollectionStringDataSource stringData = new RpcCollectionStringDataSource(sourceKey, typedDataCollectionString);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, String.class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<String> actualStringList  = (List) actualArg.getValue();
        String actualString = actualStringList.get(0);
        assertEquals(actualString, expectedString);
    }

    @Test
    public void rpcStringCollectionDataSource_To_List_string() {
        String sourceKey = "sourceKey";
        String expectedString = "Example String";

        List<String> input = new ArrayList<String>();
        input.add(expectedString);

        Builder a = TypedDataCollectionString.newBuilder();
        a.addAllString(input);

        TypedDataCollectionString typedDataCollectionString = a.build();

        RpcCollectionStringDataSource stringData = new RpcCollectionStringDataSource(sourceKey, typedDataCollectionString);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, Utility.getActualType(String[].class));
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<String> actualStringList  = (List) actualArg.getValue();
        String actualString = actualStringList.get(0);
        assertEquals(actualString, expectedString);
    }

    @Test
    public void rpcStringCollectionDataSource_No_Generic_To_List_String() {
        String sourceKey = "sourceKey";
        String expectedString = "Example String";

        List<String> input = new ArrayList<String>();
        input.add(expectedString);

        Builder a = TypedDataCollectionString.newBuilder();
        a.addAllString(input);

        TypedDataCollectionString typedDataCollectionString = a.build();

        RpcCollectionStringDataSource stringData = new RpcCollectionStringDataSource(sourceKey, typedDataCollectionString);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, List.class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<String> actualStringList  = (List) actualArg.getValue();
        String actualString = actualStringList.get(0);
        assertEquals(actualString, expectedString);
    }




}
