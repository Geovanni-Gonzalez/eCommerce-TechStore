package com.techstore.catalog.service;

import com.techstore.catalog.dto.ProductPageResponse;
import com.techstore.catalog.dto.ProductResponse;
import com.techstore.catalog.repository.ProductRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de lectura del catálogo público.
 *
 * La capa de servicio valida parámetros y pagina; el repositorio conoce SQL
 * y el futuro controlador traducirá resultados y errores a HTTP.
 */
@Service
public class ProductService {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 100;

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = Objects.requireNonNull(productRepository);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse listActiveProducts(int page, int size) {
        validatePage(page, size);

        long totalElements = productRepository.countActive();
        long totalPages = totalElements / size
                + (totalElements % size == 0 ? 0 : 1);
        long offset = (long) page * size;

        List<ProductResponse> items = offset >= totalElements
                ? List.of()
                : productRepository.findActivePage(size, offset)
                        .stream()
                        .map(ProductResponse::from)
                        .toList();

        return new ProductPageResponse(
                items, page, size, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public Optional<ProductResponse> findActiveProductById(long id) {
        if (id < 1) {
            throw new IllegalArgumentException(
                    "El identificador del producto debe ser mayor que cero");
        }
        return productRepository.findActiveById(id)
                .map(ProductResponse::from);
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "El número de página no puede ser negativo");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "El tamaño de página debe estar entre 1 y " + MAX_SIZE);
        }
    }
}
