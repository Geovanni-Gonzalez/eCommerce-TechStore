package com.techstore.inventory.repository;

import com.techstore.inventory.domain.InventoryBalance;
import com.techstore.inventory.domain.InventoryMovement;
import java.util.List;
import java.util.Optional;

/** Puerto de lectura para BKL-026 y panel BKL-046; sin mutaciones. */
public interface InventoryReadRepository {
    Optional<InventoryBalance> findBalance(long productId);
    List<InventoryBalance> listBalances(int limit, long offset);
    List<InventoryMovement> findMovements(long productId, int limit, long offset);
}
