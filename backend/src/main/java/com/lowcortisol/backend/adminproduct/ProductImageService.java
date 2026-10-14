package com.lowcortisol.backend.adminproduct;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageService {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final Path directory;

    public ProductImageService(@Value("${app.catalog.image-directory:./uploads/product-images}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public String upload(MultipartFile file) {
        if (file.isEmpty()) throw invalid();
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(MAX_BYTES + 1); }
        catch (IOException exception) { throw invalid(); }
        if (bytes.length > MAX_BYTES) throw tooLarge();
        String format;
        BufferedImage image;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) throw invalid();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 10000000) throw invalid();
                image = reader.read(0);
                if (image == null) throw invalid();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException exception) { throw invalid(); }

        // Store a decoded/re-encoded image under a server-generated name, without original metadata or trailing payloads.
        byte[] sanitized;
        try (var output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) throw invalid();
            sanitized = output.toByteArray();
        } catch (IOException exception) { throw invalid(); }
        if (sanitized.length > MAX_BYTES) throw tooLarge();
        String name = UUID.randomUUID() + (format.equals("png") ? ".png" : ".jpg");
        Path path = directory.resolve(name);
        boolean created = false;
        try {
            Files.createDirectories(directory);
            try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                output.write(sanitized);
            }
        } catch (IOException exception) {
            if (created) {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            }
            throw new AdminProductException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_STORAGE_UNAVAILABLE", "Unable to save the image");
        }
        return name;
    }

    public Resource image(String name) {
        if (!name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpg)")) {
            throw AdminProductException.missing("Image");
        }
        Path path = directory.resolve(name);
        if (!Files.isRegularFile(path)) throw AdminProductException.missing("Image");
        return new FileSystemResource(path);
    }

    private AdminProductException invalid() {
        return new AdminProductException(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_IMAGE", "Upload a valid PNG or JPEG up to 4096 pixels per side and 10 million pixels total");
    }

    private AdminProductException tooLarge() {
        return new AdminProductException(HttpStatus.PAYLOAD_TOO_LARGE, "PRODUCT_IMAGE_TOO_LARGE", "Images must be at most 5 MiB");
    }
}
