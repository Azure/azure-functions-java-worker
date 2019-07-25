package com.microsoft.azure.functions.worker.binding.tests;

import com.microsoft.azure.functions.rpc.messages.CollectionSInt64;
import com.microsoft.azure.functions.rpc.messages.CollectionSInt64.Builder;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.RpcCollectionLongDataSource;
import org.junit.Test;

import java.lang.invoke.WrongMethodTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class  RpcCollectionLongDataSourceTest{
    @Test
    public void rpcCollectionSInt64DataSource_To_Long_Object_Array() {
        String sourceKey = "sourceKey";
        Long expectedLong = 1L;

        List<Long> input = new ArrayList<Long>();
        input.add(expectedLong);

        Builder a = CollectionSInt64.newBuilder();
        a.addAllSint64(input);

        CollectionSInt64 typedDataCollectionLong = a.build();

        RpcCollectionLongDataSource stringData = new RpcCollectionLongDataSource(sourceKey, typedDataCollectionLong);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, Long[].class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        Long[] actualLongArray = (Long[]) actualArg.getValue();
        Long actualLong = actualLongArray[0];
        assertEquals(actualLong, expectedLong);
    }

    @Test
    public void rpcCollectionDoubleDataSource_To_long_Array() {
        String sourceKey = "sourceKey";
        Long expectedLong = 1L;

        List<Long> input = new ArrayList<Long>();
        input.add(expectedLong);

        Builder a = CollectionSInt64.newBuilder();
        a.addAllSint64(input);

        CollectionSInt64 typedDataCollectionLong = a.build();

        RpcCollectionLongDataSource stringData = new RpcCollectionLongDataSource(sourceKey, typedDataCollectionLong);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, long[].class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        long[] actualLongArray = (long[]) actualArg.getValue();
        Long actualLong = actualLongArray[0];
        assertEquals(actualLong, expectedLong);
    }

    @Test
    public void rpcCollectionSInt64DataSource_default_To_List_Long() {
        String sourceKey = "sourceKey";
        Long expectedLong = 1L;

        List<Long> input = new ArrayList<Long>();
        input.add(expectedLong);

        Builder a = CollectionSInt64.newBuilder();
        a.addAllSint64(input);

        CollectionSInt64 typedDataCollectionLong = a.build();

        RpcCollectionLongDataSource stringData = new RpcCollectionLongDataSource(sourceKey, typedDataCollectionLong);

        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, String.class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<Long> actualLongList  = (List) actualArg.getValue();
        Long actualLong = actualLongList.get(0);
        assertEquals(actualLong, expectedLong);
    }

    @Test
    public void rpcCollectionSInt64DataSource_To_List_Long() {
        String sourceKey = "sourceKey";
        Long expectedLong = 1L;

        List<Long> input = new ArrayList<Long>();
        input.add(expectedLong);

        Builder a = CollectionSInt64.newBuilder();
        a.addAllSint64(input);

        CollectionSInt64 typedDataCollectionLong = a.build();

        RpcCollectionLongDataSource stringData = new RpcCollectionLongDataSource(sourceKey, typedDataCollectionLong);


        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, Utility.getActualType(Long.class));
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<Long> actualLongList  = (List) actualArg.getValue();
        Long actualLong = actualLongList.get(0);
        assertEquals(actualLong, expectedLong);
    }

    @Test
    public void rpcCollectionSInt64DataSource_No_Generic_To_List_Long() {
        String sourceKey = "sourceKey";
        Long expectedLong = 1L;

        List<Long> input = new ArrayList<Long>();
        input.add(expectedLong);

        Builder a = CollectionSInt64.newBuilder();
        a.addAllSint64(input);

        CollectionSInt64 typedDataCollectionLong = a.build();

        RpcCollectionLongDataSource stringData = new RpcCollectionLongDataSource(sourceKey, typedDataCollectionLong);


        Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, List.class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<Long> actualLongList  = (List) actualArg.getValue();
        Long actualLong = actualLongList.get(0);
        assertEquals(actualLong, expectedLong);
    }
}
