package com.azfs;

import com.google.inject.AbstractModule;

public class BasicModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Communicator.class).to(DefaultCommunicatorImpl.class);
    }
}
