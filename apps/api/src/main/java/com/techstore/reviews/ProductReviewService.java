package com.techstore.reviews;

import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Solo un CLIENT con compra entregada MANUAL y sin reseña activa. */
@Service
public class ProductReviewService {
    private final JdbcProductReviewRepository repository;

    public ProductReviewService(JdbcProductReviewRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Transactional(readOnly=true)
    public ReviewEligibility eligibility(long userId,long productId) {
        requireProduct(userId,productId);
        boolean already = repository.hasActiveReview(userId,productId);
        boolean delivered = repository.hasPhysicalDelivery(userId,productId);
        return new ReviewEligibility(productId,!already && delivered,already,
                already ? "ALREADY_REVIEWED"
                        : delivered ? "ELIGIBLE" : "NOT_DELIVERED");
    }

    @Transactional(readOnly=true)
    public Optional<ProductReview> mine(long userId,long productId) {
        requireProduct(userId,productId);
        return repository.findMine(userId,productId);
    }

    @Transactional
    public ProductReview create(long userId,long productId,ReviewInput input) {
        requireProduct(userId,productId);
        Objects.requireNonNull(input);
        if (!repository.hasPhysicalDelivery(userId,productId)
                || repository.hasActiveReview(userId,productId)) {
            throw new ReviewNotEligibleException();
        }
        // V23 revalida la entrega en PostgreSQL y el índice UNIQUE parcial
        // impide duplicados incluso bajo dos solicitudes CLIENT simultáneas.
        return repository.insert(userId,productId,input);
    }

    private void requireProduct(long userId,long productId) {
        if (userId < 1 || productId < 1 || !repository.productExists(productId))
            throw new ReviewProductNotFoundException();
    }
}
