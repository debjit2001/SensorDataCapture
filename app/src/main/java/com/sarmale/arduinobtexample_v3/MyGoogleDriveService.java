package com.sarmale.arduinobtexample_v3;

import android.content.Context;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class MyGoogleDriveService {
    private final Drive driveService;

    public MyGoogleDriveService(Context context) throws IOException {
        InputStream credentialsStream = context.getResources().openRawResource(R.raw.cred);

        GoogleCredential credentials = GoogleCredential.fromStream(credentialsStream)
                .createScoped(Collections.singleton(DriveScopes.DRIVE));

        driveService = new Drive.Builder(
                new com.google.api.client.http.javanet.NetHttpTransport(),
                new com.google.api.client.json.jackson2.JacksonFactory(),
                credentials)
                .setApplicationName("CSIIMUData")
                .build();
    }

    public Drive getDriveService() {
        return driveService;
    }
}
