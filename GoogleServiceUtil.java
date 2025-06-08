package com.myproject.gdocs2slides;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.slides.v1.SlidesScopes;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class GoogleServiceUtil {

    private static final String APPLICATION_NAME = "GDocs to Slides";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    private static final List<String> SCOPES = Arrays.asList(
        DocsScopes.DOCUMENTS,
        SlidesScopes.PRESENTATIONS
    );
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    public static Docs getDocsService() throws Exception {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credentials = getCredentials(httpTransport);
        return new Docs.Builder(httpTransport, JSON_FACTORY, credentials)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static Slides getSlidesService() throws Exception {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credentials = getCredentials(httpTransport);
        return new Slides.Builder(httpTransport, JSON_FACTORY, credentials)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private static Credential getCredentials(final HttpTransport httpTransport) throws Exception {
        InputStream in = GoogleServiceUtil.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new Exception("Resource not found: " + CREDENTIALS_FILE_PATH + ". Ensure credentials.json is in the classpath.");
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(Paths.get(TOKENS_DIRECTORY_PATH).toFile()))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8889).build();
        Credential credential = null;

        try {
            credential = flow.loadCredential("user");
            if (credential == null || !credential.refreshToken()) {
                System.out.println("No valid token found or refresh failed. Starting authorization process...");
                credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
                System.out.println("Authorization successful. New token stored.");
            } else {
                System.out.println("Using existing valid token.");
            }
        } catch (TokenResponseException e) {
            if (e.getDetails() != null && "invalid_grant".equals(e.getDetails().getError())) {
                System.out.println("Token expired or revoked. Removing old token and re-authorizing...");
                flow.getCredentialDataStore().delete("user"); // Clear invalid token
                credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
                System.out.println("Re-authorization successful. New token stored.");
            } else {
                throw e; // Re-throw other token errors
            }
        } finally {
            receiver.stop();
        }

        return credential;
    }
}
