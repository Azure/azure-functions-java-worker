package com.azfs.module;

import com.azfs.model.Communicator;
import dagger.Module;
import dagger.Provides;

@Module
public class FunctionModule {
    @Provides
    public Communicator provideCommunicator() {
        return new Communicator("123");
    }
}
