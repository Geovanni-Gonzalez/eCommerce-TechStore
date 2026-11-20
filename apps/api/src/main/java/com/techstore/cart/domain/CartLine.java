package com.techstore.cart.domain;

import java.math.BigDecimal;

/**
 * Vista interna SQL de una línea. El estado y precio se leen SIEMPRE del
 * catálogo actual; todavía no son snapshots comerciales de un pedido.
 */
public record CartLine(
        long productId, String sku, String name, BigDecimal unitPriceCrc,
        int quantity, int stock, boolean active
) { }
