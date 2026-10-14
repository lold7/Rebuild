package com.lowcortisol.backend.adminproduct;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProductImageController {
    private final ProductImageService images;

    public ProductImageController(ProductImageService images) { this.images = images; }

    @PostMapping(value = "/api/admin/product-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestPart("file") MultipartFile file) {
        String image = images.upload(file);
        String url = "/api/product-images/" + image;
        return ResponseEntity.created(URI.create(url)).body(Map.of("image", image, "url", url));
    }

    @GetMapping("/api/product-images/{name}")
    public ResponseEntity<Resource> image(@PathVariable String name) {
        Resource image = images.image(name);
        return ResponseEntity.ok().contentType(name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable()).body(image);
    }
}
