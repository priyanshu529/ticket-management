package com.eventticketplatform.eventservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class FileStorageServiceTest {

    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService();
        // Override fileStorageLocation to use isolated temp directory for test
        ReflectionTestUtils.setField(fileStorageService, "fileStorageLocation", tempDir);
    }

    @Nested
    @DisplayName("storeFile() Tests")
    class StoreFileTests {

        @Test
        @DisplayName("POSITIVE: storeFile successfully saves file and returns unique filename")
        void storeFile_Positive_Success() throws Exception {
            // ARRANGE
            MockMultipartFile mockFile = new MockMultipartFile(
                    "file",
                    "concert_poster.png",
                    "image/png",
                    "Sample image content".getBytes()
            );

            // ACT
            String savedFilename = fileStorageService.storeFile(mockFile);

            // ASSERT
            assertNotNull(savedFilename);
            assertTrue(savedFilename.startsWith("poster_"));
            assertTrue(savedFilename.endsWith(".png"));

            Path savedPath = tempDir.resolve(savedFilename);
            assertTrue(Files.exists(savedPath));
            assertEquals("Sample image content", Files.readString(savedPath));
        }

        @Test
        @DisplayName("POSITIVE: storeFile defaults extension to .jpg when originalFilename has no extension")
        void storeFile_Positive_DefaultExtension() throws Exception {
            // ARRANGE
            MockMultipartFile mockFile = new MockMultipartFile(
                    "file",
                    "rawfile",
                    "image/jpeg",
                    "Data".getBytes()
            );

            // ACT
            String savedFilename = fileStorageService.storeFile(mockFile);

            // ASSERT
            assertTrue(savedFilename.endsWith(".jpg"));
        }

        @Test
        @DisplayName("NEGATIVE: storeFile throws IllegalArgumentException on path traversal filename (..)")
        void storeFile_Negative_PathTraversal() {
            // ARRANGE
            MockMultipartFile badFile = new MockMultipartFile(
                    "file",
                    "../evil.png",
                    "image/png",
                    "Malicious".getBytes()
            );

            // ACT & ASSERT
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                fileStorageService.storeFile(badFile);
            });
            assertTrue(exception.getMessage().contains("invalid path sequence"));
        }
    }

    @Nested
    @DisplayName("loadFileAsResource() Tests")
    class LoadFileTests {

        @Test
        @DisplayName("POSITIVE: loadFileAsResource returns existing readable resource")
        void loadFileAsResource_Positive_Success() throws Exception {
            // ARRANGE
            String fileName = "sample_test.txt";
            Path filePath = tempDir.resolve(fileName);
            Files.writeString(filePath, "Hello World");

            // ACT
            Resource resource = fileStorageService.loadFileAsResource(fileName);

            // ASSERT
            assertNotNull(resource);
            assertTrue(resource.exists());
            assertTrue(resource.isReadable());
        }

        @Test
        @DisplayName("NEGATIVE: loadFileAsResource throws RuntimeException when file does not exist")
        void loadFileAsResource_Negative_FileNotFound() {
            // ACT & ASSERT
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                fileStorageService.loadFileAsResource("non_existent_file.jpg");
            });
            assertTrue(exception.getMessage().contains("File not found"));
        }
    }
}
