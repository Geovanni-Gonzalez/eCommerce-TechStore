package com.techstore.catalog.admin;

import com.techstore.catalog.dto.CategoryResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso ADMIN: validación, integridad y mutaciones transaccionales. */
@Service
public class CatalogAdminService {
    private final CatalogAdminRepository repository;

    public CatalogAdminService(CatalogAdminRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AdminProductPage products(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Paginación fuera de rango (size 1..100)");
        }
        long count = repository.countProducts();
        long offset = (long)page * size;
        List<AdminProduct> items = offset >= count ? List.of()
                : repository.listProducts(size,offset);
        return new AdminProductPage(items,page,size,count,
                count/size + (count%size==0?0:1));
    }

    @Transactional
    public AdminProduct addProduct(Map<String, Object> body) {
        var input = CatalogAdminInput.product(body);
        return repository.findProduct(repository.createProduct(input));
    }

    @Transactional
    public AdminProduct editProduct(long id, Map<String, Object> body) {
        positive(id);
        var input = CatalogAdminInput.product(body);
        if (repository.updateProduct(id,input)!=1) throw new CatalogAdminNotFound();
        return repository.findProduct(id);
    }

    @Transactional
    public AdminProduct changeStatus(long id, Map<String, Object> body) {
        positive(id);
        boolean active = CatalogAdminInput.active(body);
        if (repository.setProductActive(id,active)!=1) throw new CatalogAdminNotFound();
        return repository.findProduct(id);
    }

    @Transactional(readOnly=true)
    public List<CategoryResponse> categories() {
        return repository.categories();
    }

    @Transactional
    public CategoryResponse addCategory(Map<String,Object> body) {
        var name = CatalogAdminInput.categoryName(body);
        return repository.findCategory(repository.createCategory(name));
    }

    @Transactional
    public CategoryResponse renameCategory(long id, Map<String,Object> body) {
        positive(id);
        var name = CatalogAdminInput.categoryName(body);
        if(repository.renameCategory(id,name)!=1) throw new CatalogAdminNotFound();
        return repository.findCategory(id);
    }

    @Transactional
    public void deleteCategory(long id) {
        positive(id);
        if(repository.deleteCategory(id)!=1)throw new CatalogAdminNotFound();
    }

    private static void positive(long id) {
        if (id<1) throw new IllegalArgumentException("ID debe ser positivo");
    }
}
