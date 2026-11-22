package com.techstore.catalog.service;

import com.techstore.catalog.domain.CatalogFacet;
import com.techstore.catalog.domain.ProductSearchCriteria;
import com.techstore.catalog.dto.CategoryResponse;
import com.techstore.catalog.dto.ProductFilterOptionsResponse;
import com.techstore.catalog.dto.ProductPageResponse;
import com.techstore.catalog.dto.ProductResponse;
import com.techstore.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BKL-018/020/021: búsqueda pública, paginación y filtros combinables. */
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
        return searchActiveProducts(page, size, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse searchActiveProducts(int page, int size,
            String query, Long categoryId, String brand,
            BigDecimal minPriceCrc, BigDecimal maxPriceCrc) {
        validatePage(page, size);
        ProductSearchCriteria filters = new ProductSearchCriteria(
                normalize(query, 120, "La búsqueda"),
                categoryId, normalize(brand, 100, "La marca"),
                minPriceCrc, maxPriceCrc);
        validateFilters(filters);

        long totalElements = filters.isEmpty()
                ? productRepository.countActive()
                : productRepository.countActiveFiltered(filters);
        long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        long offset = (long) page * size;
        List<ProductResponse> items = offset >= totalElements ? List.of()
                : (filters.isEmpty()
                        ? productRepository.findActivePage(size, offset)
                        : productRepository.findActiveFilteredPage(filters, size, offset))
                        .stream().map(ProductResponse::from).toList();
        return new ProductPageResponse(items, page, size, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public ProductFilterOptionsResponse availableFilters() {
        List<CategoryResponse> categories = productRepository.findAvailableCategories()
                .stream().map(c -> new CategoryResponse(c.id(), c.name())).toList();
        return new ProductFilterOptionsResponse(
                categories, productRepository.findAvailableBrands());
    }

    @Transactional(readOnly = true)
    public Optional<ProductResponse> findActiveProductById(long id) {
        if (id < 1) {
            throw new IllegalArgumentException(
                    "El identificador del producto debe ser mayor que cero");
        }
        return productRepository.findActiveById(id).map(ProductResponse::from);
    }

    private static String normalize(String text, int max, String label) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.strip();
        if (trimmed.length() > max || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " contiene caracteres no permitidos");
        }
        return trimmed;
    }

    private static void validateFilters(ProductSearchCriteria filters) {
        if (filters.categoryId() != null && filters.categoryId() < 1L) {
            throw new IllegalArgumentException("La categoría debe tener un ID positivo");
        }
        for (BigDecimal price : new BigDecimal[]{
                filters.minPriceCrc(), filters.maxPriceCrc()}) {
            if (price != null && (price.signum() < 0
                    || price.scale() > 2 || price.precision() > 12)) {
                throw new IllegalArgumentException("El precio debe ser CRC válido con máximo dos decimales");
            }
        }
        if (filters.minPriceCrc() != null && filters.maxPriceCrc() != null
                && filters.minPriceCrc().compareTo(filters.maxPriceCrc()) > 0) {
            throw new IllegalArgumentException("El precio mínimo excede el máximo");
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException(
                "El número de página no puede ser negativo");
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException(
                "El tamaño de página debe estar entre 1 y " + MAX_SIZE);
    }
}
