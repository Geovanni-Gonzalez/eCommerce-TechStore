package com.techstore.orders.query;

import java.util.List;

/**
 * Paginación determinista por ID descendente. nextBeforeId solo se expone
 * cuando quedan más filas; el siguiente filtro es id < nextBeforeId.
 */
public record OrderPage<T>(List<T> items, boolean hasMore, Long nextBeforeId) {
    public OrderPage {
        items = List.copyOf(items);
        if (hasMore != (nextBeforeId != null)) {
            throw new IllegalArgumentException("Cursor de página inconsistente");
        }
    }
}
