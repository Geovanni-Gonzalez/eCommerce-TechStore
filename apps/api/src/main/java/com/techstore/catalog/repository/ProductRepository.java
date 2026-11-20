package com.techstore.catalog.repository;

import com.techstore.catalog.domain.Product;
import java.util.List;
import java.util.Optional;


public interface ProductRepository {

    List<Product> findActivePage(int limit, long offset);

    Optional<Product> findActiveById(long id);

    long countActive();
}