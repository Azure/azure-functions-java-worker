package com.azfs.component;

import com.azfs.Function;
import com.azfs.module.FunctionModule;
import dagger.Component;

@Component(modules = FunctionModule.class)
public interface FunctionComponent {
    Function buildFunction();
}
