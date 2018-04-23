/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.serverless.functions.annotation;

import java.lang.annotation.*;

/**
 * This meta-annotation defines another annotation as a function parameter binding
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE})
public @interface Binding {
    /**
     * Returns the type of binding annotation
     * @return the type of binding annotation
     */
    BindingType value();
}
