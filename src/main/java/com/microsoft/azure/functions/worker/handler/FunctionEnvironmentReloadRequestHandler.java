package com.microsoft.azure.functions.worker.handler;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.rpc.messages.FunctionEnvironmentReloadResponse.Builder;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.description.FunctionMethodDescriptor;

public class FunctionEnvironmentReloadRequestHandler
    extends MessageHandler<FunctionEnvironmentReloadRequest, FunctionEnvironmentReloadResponse.Builder> {
  public FunctionEnvironmentReloadRequestHandler(JavaFunctionBroker broker) {
    super(StreamingMessage::getFunctionEnvironmentReloadRequest,
        FunctionEnvironmentReloadResponse::newBuilder,
        FunctionEnvironmentReloadResponse.Builder::setResult,
        StreamingMessage.Builder::setFunctionEnvironmentReloadResponse);

    this.broker = broker;    
  }

  public Map<String, String> EnvironmentVariables = new HashMap<>();

  @Override
  String execute(FunctionEnvironmentReloadRequest request, Builder response) throws Exception {
    EnvironmentVariables = request.getEnvironmentVariablesMap();
    if (EnvironmentVariables == null || EnvironmentVariables.isEmpty()) {    
      return String.format("Ignoring FunctionEnvironmentReloadRequest as newSettings map is either empty or null");
    }
    
    specialize(EnvironmentVariables);    
    return String.format("FunctionEnvironmentReloadRequest completed");
  }

  FunctionMethodDescriptor createFunctionDescriptor(String functionId,
      RpcFunctionMetadata metadata) {
    return new FunctionMethodDescriptor(functionId, metadata.getName(), metadata.getEntryPoint(),
        metadata.getScriptFile());
  }
  
  /*
   * This is a helper utility specifically to reload environment variables and
   * ReinitializeDetours if java language worker is started in standby mode by the functions runtime
   * and should not be used for other purposes
   */
  public void specialize(Map<String, String> newSettings) throws Exception {
    if (newSettings == null || newSettings.isEmpty()) {    
      return;
    }

    // This is required to reinitialize file detours
    newSettings.put(WebsitePlaceholderMode, "0");
    try {
        Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
        Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
        theEnvironmentField.setAccessible(true);
        Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
        env.clear();
        env.putAll(newSettings);
        Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
        theCaseInsensitiveEnvironmentField.setAccessible(true);
        Map<String, String> cienv = (Map<String, String>)     theCaseInsensitiveEnvironmentField.get(null);
        cienv.clear();
        cienv.putAll(newSettings);
      } catch (NoSuchFieldException e) {
        Class[] classes = Collections.class.getDeclaredClasses();
        Map<String, String> env = System.getenv();
        for(Class cl : classes) {
          if("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Object obj = field.get(env);
            Map<String, String> map = (Map<String, String>) obj;
            map.clear();
            map.putAll(newSettings);
          }
        }
      }
      
      String  azureWebsiteInstanceId = System.getenv(AzureWebsiteInstanceId);
      if (azureWebsiteInstanceId != null && azureWebsiteInstanceId != "")
      {
    	  PicoHelper picoHelperInstance = PicoHelper.INSTANCE;
          picoHelperInstance.ReinitializeDetours();
      }      
    }
	
  private final JavaFunctionBroker broker;
  public final String AzureWebsiteInstanceId = "WEBSITE_INSTANCE_ID";
  public final String WebsitePlaceholderMode = "WEBSITE_PLACEHOLDER_MODE";
}
