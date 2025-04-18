package com.microsoft.azure.functions.worker.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;

public final class FunctionsResourceDetector {
    private static final String CLOUD_PROVIDER = "cloud.provider";
    private static final String CLOUD_PLATFORM = "cloud.platform";
    private static final String CLOUD_REGION = "cloud.region";
    private static final String CLOUD_RESOURCE_ID = "cloud.resource.id";
    private static final String DEPLOYMENT_ENVIRONMENT = "deployment.environment";
    private static final String SERVICE_NAME = "service.name";

    // Well-known Azure Functions environment variables
    private static final String WEBSITE_SITE_NAME = "WEBSITE_SITE_NAME";
    private static final String REGION_NAME = "REGION_NAME";
    private static final String WEBSITE_RESOURCE_GROUP = "WEBSITE_RESOURCE_GROUP";
    private static final String WEBSITE_OWNER_NAME = "WEBSITE_OWNER_NAME";
    private static final String WEBSITE_SLOT_NAME = "WEBSITE_SLOT_NAME";

    public static Resource getResource() {
        String siteName = System.getenv(WEBSITE_SITE_NAME);
        String region = System.getenv(REGION_NAME);
        String resourceGroup = System.getenv(WEBSITE_RESOURCE_GROUP);
        String ownerName = System.getenv(WEBSITE_OWNER_NAME);
        String slotName = System.getenv(WEBSITE_SLOT_NAME);

        AttributesBuilder attrBuilder = Attributes.builder();

        // Always set some form of service.name
        if (siteName != null && !siteName.isEmpty()) {
            attrBuilder.put(SERVICE_NAME, siteName);
            // We are running in Azure
            attrBuilder.put(CLOUD_PROVIDER, "azure");
            attrBuilder.put(CLOUD_PLATFORM, "azure_functions");
        } else {
            // For local dev or fallback
            attrBuilder.put(SERVICE_NAME, "java-function-app");
        }

        // If region is available
        if (region != null && !region.isEmpty()) {
            attrBuilder.put(CLOUD_REGION, region);
        }

        // If we can parse subscription/resource group from WEBSITE_OWNER_NAME
        // WEBSITE_OWNER_NAME typically looks like: <subscription>+<something else>
        String subscriptionId = null;
        if (ownerName != null && !ownerName.isEmpty()) {
            int idx = ownerName.indexOf('+');
            if (idx > 0) {
                subscriptionId = ownerName.substring(0, idx);
            }
        }

        if (subscriptionId != null && resourceGroup != null && siteName != null) {
            String resourceId = String.format(
                    "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Web/sites/%s",
                    subscriptionId, resourceGroup, siteName
            );
            attrBuilder.put(CLOUD_RESOURCE_ID, resourceId);
        }

        // Determine deployment environment (slot), default to "production" if not set
        if (slotName == null || slotName.isEmpty()) {
            slotName = "production";
        }
        attrBuilder.put(DEPLOYMENT_ENVIRONMENT, slotName);

        // Build resource
        return Resource.create(attrBuilder.build());
    }
}

