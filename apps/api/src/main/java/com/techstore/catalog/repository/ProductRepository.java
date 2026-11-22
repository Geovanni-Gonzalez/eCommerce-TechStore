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

    List<Product> findActiveFilteredPage(ProductSearchCriteria criteria, int limit, long offset);
    long countActiveFiltered(ProductSearchCriteria criteria);
    List<CatalogFacet> findAvailableCategories();
    List<String> findAvailableBrands();
}
