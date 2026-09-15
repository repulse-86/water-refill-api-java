package com.example.waterrefillapijava.product.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "webp"
    );

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(uploadDir)
                .toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the 5 MB limit.");
        }

        final String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        final String extension = extractExtension(originalFileName).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                "File type '" + extension + "' is not allowed. Accepted types: jpg, jpeg, png, gif, webp."
            );
        }

        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType().toLowerCase())) {
            throw new IllegalArgumentException(
                "Content type '" + file.getContentType() + "' is not allowed."
            );
        }

        final String fileName = UUID.randomUUID() + "." + extension;

        try {
            final Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    public void delete(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (!url.startsWith("/uploads/")) {
            return;
        }
        final String fileName = url.substring("/uploads/".length());
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return;
        }
        try {
            final Path target = fileStorageLocation.resolve(fileName).normalize();
            if (!target.startsWith(fileStorageLocation)) {
                return;
            }
            Files.deleteIfExists(target);
        } catch (final IOException e) {
            // Log and swallow — file deletion is best-effort after DB commit
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
