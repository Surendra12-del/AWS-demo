package com.example.aws.demo.product;

import com.example.aws.demo.metrics.CloudWatchService;
import com.example.aws.demo.storage.StorageService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Business logic for products. Persists to RDS (via {@link ProductRepository}), stores images in
 * S3 (via {@link StorageService}) and emits custom CloudWatch metrics (via {@link CloudWatchService}).
 */
@Service
public class ProductService {

    private final ProductRepository repository;
    private final StorageService storageService;
    private final CloudWatchService cloudWatchService;

    public ProductService(ProductRepository repository,
                          StorageService storageService,
                          CloudWatchService cloudWatchService) {
        this.repository = repository;
        this.storageService = storageService;
        this.cloudWatchService = cloudWatchService;
    }

    public List<Product> findAll() {
        return repository.findAll();
    }

    public Optional<Product> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public Product create(ProductRequest request) {
        Product product = new Product(request.name(), request.description(), request.price());
        Product saved = repository.save(product);
        cloudWatchService.publishCount("ProductCreated", "create");
        return saved;
    }

    @Transactional
    public Optional<Product> update(Long id, ProductRequest request) {
        return repository.findById(id).map(product -> {
            product.setName(request.name());
            product.setDescription(request.description());
            product.setPrice(request.price());
            return repository.save(product);
        });
    }

    @Transactional
    public boolean delete(Long id) {
        return repository.findById(id).map(product -> {
            storageService.delete(product.getImageKey());
            repository.delete(product);
            cloudWatchService.publishCount("ProductDeleted", "delete");
            return true;
        }).orElse(false);
    }

    /**
     * Uploads an image to S3 and attaches its key to the product.
     */
    @Transactional
    public Optional<Product> attachImage(Long id, MultipartFile file) throws IOException {
        Optional<Product> found = repository.findById(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Product product = found.get();
        // Replace any existing image.
        storageService.delete(product.getImageKey());
        String key = storageService.upload(file);
        product.setImageKey(key);
        Product saved = repository.save(product);
        cloudWatchService.publishCount("ProductImageUploaded", "upload");
        return Optional.of(saved);
    }

    public Optional<StorageService.DownloadedObject> downloadImage(Long id) {
        return repository.findById(id)
                .filter(p -> p.getImageKey() != null && !p.getImageKey().isBlank())
                .map(p -> storageService.download(p.getImageKey()));
    }
}
