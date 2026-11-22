package com.techstore.reviews;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductReviewPublicTest {
    @Test
    void listsOnlyActivePublicProjectionThroughRepository() {
        JdbcProductReviewRepository repository = mock(JdbcProductReviewRepository.class);
        ProductReviewService service = new ProductReviewService(repository);
        when(repository.productExists(9L)).thenReturn(true);
        var review = new PublicProductReview(5, "Excelente producto", OffsetDateTime.parse("2026-11-21T12:00:00Z"));
        when(repository.findPublic(9L)).thenReturn(List.of(review));
        assertEquals(List.of(review), service.listPublic(9L));
        verify(repository).findPublic(9L);
    }

    @Test
    void missingOrInvalidProductsCannotListReviews() {
        JdbcProductReviewRepository repository = mock(JdbcProductReviewRepository.class);
        ProductReviewService service = new ProductReviewService(repository);
        assertThrows(ReviewProductNotFoundException.class, () -> service.listPublic(0));
        assertThrows(ReviewProductNotFoundException.class, () -> service.listPublic(19));
        verify(repository, never()).findPublic(anyLong());
    }

    @Test
    void publicDtoContainsNoIdentityOrOrderFields() {
        var names = java.util.Arrays.stream(PublicProductReview.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertEquals(List.of("rating", "comment", "createdAt"), names);
    }
}
