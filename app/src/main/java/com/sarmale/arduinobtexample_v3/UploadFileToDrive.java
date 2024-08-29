package com.sarmale.arduinobtexample_v3;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.util.Collections;

public class UploadFileToDrive {

    private Drive driveService;

    public UploadFileToDrive(Drive driveService) {
        this.driveService = driveService;
    }

    public void uploadFile(String filePath, String folderId, String fileName) {

        System.out.println("Service received: "+driveService.toString());
        // File's metadata.
        File fileMetadata = new File();
        fileMetadata.setName(fileName);

        // If you want to upload to a specific folder
        fileMetadata.setParents(Collections.singletonList(folderId));

        System.out.println("FileMetaData-Name:"+fileMetadata.getName());
        System.out.println("FileMetaData-Parent:"+fileMetadata.getParents());

        // File's content.
        java.io.File file = new java.io.File(filePath);

        System.out.println("FileInfo-Name"+file.getName());
        System.out.println("FileInfo-Path"+file.getAbsolutePath());
        System.out.println("FileInfo-Parent"+file.getParent());

        FileContent mediaContent = new FileContent("text/plain", file);

        try {
            // Upload the file
            System.out.println("Hi from Try in upload file class");
            File uploadedFile = driveService.files().create(fileMetadata, mediaContent).setFields("id").execute();
            if (uploadedFile != null) {
                System.out.println("Upload successful!");
                System.out.println("File ID: " + uploadedFile.getId());
                System.out.println("File Name: " + uploadedFile.getName());
                System.out.println("File Parents: " + uploadedFile.getParents());
            } else {
                System.out.println("Upload failed, no response received.");
            }
        } catch (GoogleJsonResponseException e) {
            System.err.println("Google API error: " + e.getDetails().getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

