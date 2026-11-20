package com.techstore.cart.repository;

import com.techstore.cart.domain.CartLine;
import java.util.List;

/**
 * Abstracción de almacenamiento por usuario, sin recibir identificadores de
 * carrito externos. Los cambios devuelven false si no fueron aplicables.
 */
public interface CartRepository {
    List<CartLine> findItems(long userId);
    boolean containsProduct(long userId, long productId);
    void ensureCart(long userId);
    boolean incrementProduct(long userId, long productId, int quantity);
    boolean setQuantity(long userId, long productId, int quantity);
    boolean removeProduct(long userId, long productId);
    void clear(long userId);
}
