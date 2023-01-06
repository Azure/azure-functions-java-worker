
package com.microsoft.azure.functions.worker.binding.tests;

import com.microsoft.azure.functions.rpc.messages.CollectionDouble;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.RpcCollectionDoubleDataSource;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.junit.jupiter.api.Test;

import java.lang.invoke.WrongMethodTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RpcCollectionDoubleDataSourceTest {
    @Test
    public void rpcCollectionDoubleDataSource_To_Double_Object_Array() {
        String sourceKey = "sourceKey";
        Double expectedDouble = 1.1;

        List<Double> input = new ArrayList<Double>();
        input.add(expectedDouble);

        CollectionDouble.Builder a = CollectionDouble.newBuilder();
        a.addAllDouble(input);

        CollectionDouble typedDataCollectionDouble = a.build();

        RpcCollectionDoubleDataSource data = new RpcCollectionDoubleDataSource(sourceKey, typedDataCollectionDouble);

        Optional<BindingData> actualBindingData = data.computeByName(sourceKey, Double[].class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        Double[] actualDoublegArray = (Double[]) actualArg.getValue();
        Double actualDouble = actualDoublegArray[0];
        assertEquals(actualDouble, expectedDouble);
    }

    @Test
    public void rpcCollectionDoubleDataSource_To_double_Array() {
        String sourceKey = "sourceKey";
        double expectedDouble = 1.1;

        List<Double> input = new ArrayList<Double>();
        input.add(expectedDouble);

        CollectionDouble.Builder a = CollectionDouble.newBuilder();
        a.addAllDouble(input);

        CollectionDouble typedDataCollectionDouble = a.build();

        RpcCollectionDoubleDataSource data = new RpcCollectionDoubleDataSource(sourceKey, typedDataCollectionDouble);

        Optional<BindingData> actualBindingData = data.computeByName(sourceKey, double[].class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        double[] actualDoubleArray = (double[]) actualArg.getValue();
        double actualDouble = actualDoubleArray[0];
        assertEquals("" + actualDouble, "" + expectedDouble);
    }

    @Test
    public void rpcCollectionDoubleDataSource_default_To_List_Double() {
        String sourceKey = "sourceKey";
        Double expectedDouble = 1.1;

        List<Double> input = new ArrayList<Double>();
        input.add(expectedDouble);

        CollectionDouble.Builder a = CollectionDouble.newBuilder();
        a.addAllDouble(input);

        CollectionDouble typedDataCollectionDouble = a.build();

        RpcCollectionDoubleDataSource data = new RpcCollectionDoubleDataSource(sourceKey, typedDataCollectionDouble);

        Optional<BindingData> actualBindingData = data.computeByName(sourceKey, String.class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<Double> actualDoubleList  = (List) actualArg.getValue();
        Double actualLong = actualDoubleList.get(0);
        assertEquals(actualLong, expectedDouble);
    }

    @Test
    public void rpcCollectionDoubleDataSource_To_List_Double() {
        String sourceKey = "sourceKey";
        Double expectedDouble = 1.1;

        List<Double> input = new ArrayList<Double>();
        input.add(expectedDouble);

        CollectionDouble.Builder a = CollectionDouble.newBuilder();
        a.addAllDouble(input);

        CollectionDouble typedDataCollectionDouble = a.build();

        RpcCollectionDoubleDataSource data = new RpcCollectionDoubleDataSource(sourceKey, typedDataCollectionDouble);

        Optional<BindingData> actualBindingData = data.computeByName(sourceKey, TypeUtils.parameterize(List.class, Double.class));
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<Double> actualDoubleList  = (List) actualArg.getValue();
        Double actualLong = actualDoubleList.get(0);
        assertEquals(actualLong, expectedDouble);
    }

    @Test
    public void rpcCollectionDoubleDataSource_No_Generic_To_List_Long() {
        String sourceKey = "sourceKey";
        Double expectedDouble = 1.1;

        List<Double> input = new ArrayList<Double>();
        input.add(expectedDouble);

        CollectionDouble.Builder a = CollectionDouble.newBuilder();
        a.addAllDouble(input);

        CollectionDouble typedDataCollectionDouble = a.build();

        RpcCollectionDoubleDataSource data = new RpcCollectionDoubleDataSource(sourceKey, typedDataCollectionDouble);

        Optional<BindingData> actualBindingData = data.computeByName(sourceKey, List.class);
        BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
        List<Double> actualDoubleList  = (List) actualArg.getValue();
        Double actualLong = actualDoubleList.get(0);
        assertEquals(actualLong, expectedDouble);
    }
}

