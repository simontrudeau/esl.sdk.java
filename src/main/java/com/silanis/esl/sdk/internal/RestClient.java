package com.silanis.esl.sdk.internal;

import com.silanis.esl.sdk.EslException;
import com.silanis.esl.sdk.io.Streams;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import static com.silanis.esl.sdk.io.Streams.toByteArray;

public class RestClient {

    private static final ResponseHandler<byte[]> BYTES_HANDLER = new BytesHandler();
    private static final ResponseHandler<String> JSON_HANDLER = new JsonHandler();

    private final String apiToken;
    private final Support support = new Support();

    public RestClient(String apiToken) {
        this.apiToken = apiToken;
    }

    public String post(String path, String jsonPayload) throws IOException, HttpException, URISyntaxException {
        support.logRequest("POST", path, jsonPayload);

        HttpPost post = new HttpPost( path );
        StringEntity body = new StringEntity(jsonPayload);

        body.setContentType("application/json");
        post.setEntity(body);

        String response = execute(post, apiToken, JSON_HANDLER);

        support.logResponse(response);
        return response;
    }

    public void postMultipartFile(String path, String fileName, byte[] fileBytes, String jsonPayload) throws IOException, HttpException, URISyntaxException {
        MultipartEntity multipart = new MultipartEntity();
        String contentType = MimeTypeUtils.getContentTypeByFileName(fileName);

        multipart.addPart("payload", new StringBody(jsonPayload));
        multipart.addPart("file", new ByteArrayBody(fileBytes, contentType, fileName));

        HttpPost post = new HttpPost( path );

        post.setEntity(multipart);

        execute(post, apiToken, JSON_HANDLER);
    }

    private static <T> T execute(HttpUriRequest request, String apiToken, ResponseHandler<T> handler) throws IOException {
        HttpClient client = new DefaultHttpClient();

        request.setHeader( "Authorization", "Basic " + apiToken );

        try {
            HttpResponse response = client.execute(request);

            if (response.getStatusLine().getStatusCode() >= 400) {
                throw new CommunicationException(request.getRequestLine().getMethod(),
                        request.getRequestLine().getUri(),
                        response.getStatusLine().getStatusCode(),
                        response.getStatusLine().getReasonPhrase());
            }

            InputStream bodyContent = response.getEntity().getContent();

            return handler.extract(bodyContent);
        }
        finally {
            client.getConnectionManager().shutdown();
        }
    }

    public String get(String path) throws IOException, HttpException, URISyntaxException {
        support.logRequest("GET", path);
        HttpGet get = new HttpGet( path );

        String response = execute(get, apiToken, JSON_HANDLER);

        support.logResponse(response);
        return response;
    }

    public byte[] getBytes(String path) throws IOException, HttpException, URISyntaxException {
        support.logRequest("GET", path);
        HttpGet get = new HttpGet( path );

        return execute(get, apiToken, BYTES_HANDLER);
    }

    public String delete(String path) throws HttpException, IOException, URISyntaxException {
        support.logRequest("DELETE", path);
        HttpDelete delete = new HttpDelete( path );

        return execute(delete, apiToken, JSON_HANDLER);
    }

    private static interface ResponseHandler<T> {
        T extract(InputStream input);
    }

    private static class BytesHandler implements ResponseHandler<byte[]> {

        public byte[] extract(InputStream input) {
            return toByteArray(input);
        }
    }

    private static class JsonHandler implements ResponseHandler<String> {

        public String extract(InputStream input) {
            try {
                return Streams.toString(input);
            } catch (UnsupportedEncodingException e) {
                throw new EslException("", e);
            }
        }
    }
}