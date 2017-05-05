package com.microsoft.azure.webjobs.script;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;

import io.grpc.*;
import io.grpc.stub.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class JavaHost 
{
    private static class RpcFunctionImpl extends RpcFunctionGrpc.RpcFunctionImplBase {
        @Override
        public void rpcInvokeFunction(RpcFunctionInvokeMetadata request, StreamObserver<RpcFunctionResultMetadata> responseObserver) {
            try {
                String jarPath = request.getScriptFile();
                String entryPoint = request.getEntryPoint();
                int lastPeriodIndex = entryPoint.lastIndexOf('.');
                String className = entryPoint.substring(0, lastPeriodIndex);
                String methodName = entryPoint.substring(lastPeriodIndex + 1);

                System.out.println(jarPath);
                System.out.println(className);
                System.out.println(methodName);

                URL jarUrl = new File(jarPath).toURI().toURL();
                URLClassLoader loader = URLClassLoader.newInstance(new URL[] { jarUrl });
                Class<?> clazz = Class.forName(className, true, loader);

                Class<?> paramType = String.class;
                Method function = clazz.getMethod(methodName, paramType);

                Object paramValue = request.getInputValue().getStringValue();
                Object output = function.invoke(null, new Object [] { paramValue } );
                System.out.println(output);
                RpcDataValue.Builder outputValueBuilder = RpcDataValue.newBuilder();
                outputValueBuilder.setStringValue(output.toString());

                RpcFunctionResultMetadata reply = RpcFunctionResultMetadata.newBuilder()
                                                  .setInvocationId(request.getInvocationId())
                                                  .setOutputValue(outputValueBuilder.build())
                                                  .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            } catch (Exception ex) {
                ex.printStackTrace();
                RpcFunctionResultMetadata reply = RpcFunctionResultMetadata.newBuilder()
                                                  .setInvocationId(request.getInvocationId())
                                                  .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            }
        }
    }

    public static void main( String[] args )
    {
        try {
            final int port = 50051;
            Server server = ServerBuilder.forPort(port).addService(new RpcFunctionImpl()).build().start();
            System.out.println("Server started, listening on port " + port);

            System.out.println("Press any key to exit...");
            System.in.read();
            server.shutdown();
            server.awaitTermination();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
