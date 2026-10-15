package com.lowcortisol.backend.adminproduct;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class ProductImageServiceTest {
    @TempDir Path directory;

    @Test
    void filenameCollisionDoesNotDeleteOrOverwriteExistingImage() throws IOException {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Path existing = directory.resolve(id + ".png");
        Files.writeString(existing, "existing image");
        var service = new ProductImageService(directory.toString());
        var upload = png();
        try (var uuids = mockStatic(UUID.class)) {
            uuids.when(UUID::randomUUID).thenReturn(id);
            assertThatThrownBy(() -> service.upload(upload)).isInstanceOf(AdminProductException.class)
                .hasMessage("Unable to save the image");
        }
        assertThat(Files.readString(existing)).isEqualTo("existing image");
    }

    @Test
    void unavailableDirectoryReturnsStorageErrorWithoutDeletingExistingFile() throws IOException {
        Path file = directory.resolve("not-a-directory");
        Files.writeString(file, "keep this file");
        var service = new ProductImageService(file.toString());
        var upload = png();
        assertThatThrownBy(() -> service.upload(upload)).isInstanceOf(AdminProductException.class)
            .hasMessage("Unable to save the image");
        assertThat(Files.readString(file)).isEqualTo("keep this file");
    }

    private MockMultipartFile png() throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "cover.png", "image/png", output.toByteArray());
    }
}
