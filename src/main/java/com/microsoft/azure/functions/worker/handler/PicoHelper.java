package com.microsoft.azure.functions.worker.handler;

import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;

// kernel32.dll uses the __stdcall calling convention (check the function
// declaration for "WINAPI" or "PASCAL"), so extend StdCallLibrary
// Most C libraries will just extend com.sun.jna.Library,
public interface PicoHelper extends StdCallLibrary { 
    PicoHelper INSTANCE = (PicoHelper)Native.load("picohelper", PicoHelper.class, W32APIOptions.DEFAULT_OPTIONS);
    void ReinitializeDetours();
}

