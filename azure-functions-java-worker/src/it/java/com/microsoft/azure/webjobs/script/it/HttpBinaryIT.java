package com.microsoft.azure.webjobs.script.it;

import com.microsoft.azure.storage.*;
import com.microsoft.azure.storage.blob.*;
import com.microsoft.azure.webjobs.script.it.utils.RequestSpecificationProvider;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.*;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.util.*;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;

public class HttpBinaryIT {

    private static RequestSpecification spec;
    private static final String content = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras metus justo, dictum vel tortor nullam.";
    private static final byte[] contentBytes = content.getBytes();

    @BeforeClass
    public static void initSpec() {
        spec = new RequestSpecBuilder()
                .addRequestSpecification(RequestSpecificationProvider.getDefault())
                .setContentType(ContentType.TEXT)
                .setBody(Arrays.toString(contentBytes))
                .build();
    }

    @Test
    public void primitive_byte_array_over_http() {
        String responseBody = given()
                .spec(spec)
                .when()
                .post("/primitiveByteArray")
                .then()
                .assertThat().statusCode(200).and().extract().body().asString();

        assertThat(responseBody).isEqualTo("Received " + contentBytes.length + " bytes byte[] data");
    }

    @Test
    public void class_byte_array_over_http() {
        byte[] bytes = content.getBytes();

        String responseBody = given()
                .spec(spec)
                .when()
                .post("/classByteArray")
                .then()
                .assertThat().statusCode(200).and().extract().body().asString();

       assertThat(responseBody).isEqualTo("Received " + contentBytes.length + " bytes Byte[] data");
    }

    @Test
    public void http_upload_file_with_blob_output_binding()
            throws URISyntaxException, InvalidKeyException, StorageException, InterruptedException, IOException {

        final File file = new File(this.getClass().getClassLoader().getResource("testdata.txt").getFile());

        given()
            .spec(spec)
            .contentType("multipart/text")
            .multiPart(file)
            .when()
            .post("/httpBinaryUpload")
            .then()
            .assertThat().statusCode(200);

        Thread.sleep(5000); //wait to make sure blob storage upload is done

        File downloadedFile = getUploadedFileFromBlob();

        String expected = new String(Files.readAllBytes(file.toPath()));
        String actual = new String(Files.readAllBytes(downloadedFile.toPath()));

        assertThat(downloadedFile).exists();
        assertThat(actual).contains(expected);
    }

    private File getUploadedFileFromBlob() throws URISyntaxException, StorageException, IOException, InvalidKeyException {
        Properties properties = new Properties();
        properties.load(this.getClass().getClassLoader().getResourceAsStream("config.properties"));

        String storageConnectionString = properties.getProperty("blobStorageAccount");
        CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);
        CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
        CloudBlobContainer container = blobClient.getContainerReference("functions");

        CloudBlockBlob blob = container.getBlockBlobReference(properties.getProperty("blobFilename"));

        final String path = properties.getProperty("executionPath") + "/testdata.txt";
        blob.downloadToFile(path);

        File downloadedFile = new File(path);

        return downloadedFile;
    }
}
