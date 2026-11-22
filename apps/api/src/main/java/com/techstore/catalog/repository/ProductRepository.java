package com.techstore.catalog.repository;

import com.techstore.catalog.domain.Product;
import com.techstore.catalog.domain.CatalogFacet;
import com.techstore.catalog.domain.ProductSearchCriteria;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    List<Product> findActivePage(int limit, long offset);
    Optional<Product> findActiveById(long id);
    long countActive();

    /** Métodos nuevos opcionales para implementaciones existentes de lectura. */
    default List<Product> findActiveFilteredPage(
            ProductSearchCriteria criteria, int limit, long offset) {
        throw new UnsupportedOperationException("Esta implementación no dispone de filtros");
    }
    default long countActiveFiltered(ProductSearchCriteria criteria) {
        throw new UnsupportedOperationException("Esta implementación no dispone de filtros");
    }
    default List<CatalogFacet> findAvailableCategories() {
        return List.of();
    }
    default List<String> findAvailableBrands() {
        return List.of();
    }
}
