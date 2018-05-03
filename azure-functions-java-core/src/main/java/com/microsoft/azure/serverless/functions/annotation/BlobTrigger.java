/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.serverless.functions.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>The following example shows a storage blob trigger which logs the blob filename as well as its size:</p>
 *
 * <pre>{@literal @}FunctionName("blobprocessor")
 * public void run(
 *    {@literal @}BlobTrigger(name = "file",
 *                  dataType = "binary",
 *                  path = "myblob/filepath",
 *                  connection = "myconnvarname") byte[] content,
 *    {@literal @}BindingName("name") String filename,
 *     final ExecutionContext context
 * ) {
 *     context.getLogger().info("Name: " + name + " Size: " + content.length + " bytes");
 * }</pre>
 *
 * @see com.microsoft.azure.serverless.functions.annotation.BindingName
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface BlobTrigger {
    String name();

    String dataType() default "";

    String path();

    String connection() default "";
}
