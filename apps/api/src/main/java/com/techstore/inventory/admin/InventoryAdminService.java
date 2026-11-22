package com.techstore.inventory.admin;

import com.techstore.inventory.domain.InventoryBalance;
import com.techstore.inventory.repository.InventoryReadRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BKL-026: casos de uso de inventario, separados del CRUD del catálogo.
 * @Transactional incluye SELECT FOR UPDATE, UPDATE de stock e INSERT de
 * auditoría: un fallo revierte la operación completa.
 */
@Service
public class InventoryAdminService {
    private static final int MAX_STOCK = Integer.MAX_VALUE;
    private final InventoryReadRepository balances;
    private final JdbcInventoryAdjustmentRepository adjustments;

    public InventoryAdminService(InventoryReadRepository balances,
            JdbcInventoryAdjustmentRepository adjustments) {
        this.balances=Objects.requireNonNull(balances);
        this.adjustments=Objects.requireNonNull(adjustments);
    }

    @Transactional(readOnly=true)
    public InventoryBalancePage listBalances(int page,int size) {
        long offset=offset(page,size);
        long total=adjustments.countProducts();
        List<InventoryBalance> rows=offset>=total?List.of()
                :balances.listBalances(size,offset);
        return new InventoryBalancePage(rows,page,size,total,
                total/size+(total%size==0?0:1));
    }

    @Transactional(readOnly=true)
    public InventoryBalance balance(long productId) {
        positive(productId);
        return balances.findBalance(productId)
                .orElseThrow(InventoryProductNotFound::new);
    }

    @Transactional(readOnly=true)
    public InventoryMovementPage movements(long productId,int page,int size) {
        positive(productId);
        if(balances.findBalance(productId).isEmpty()) {
            throw new InventoryProductNotFound();
        }
        long offset=offset(page,size);
        long total=adjustments.countMovements(productId);
        var items=offset>=total?List.<InventoryMovementAudit>of()
                :adjustments.listMovements(productId,size,offset);
        return new InventoryMovementPage(productId,items,page,size,total,
                total/size+(total%size==0?0:1));
    }

    @Transactional
    public InventoryAdjustmentResult adjust(
            long adminId,long productId,Map<String,Object> payload) {
        positive(adminId);
        positive(productId);
        var command=InventoryAdjustmentCommand.from(payload);
        var prior=adjustments.findByRequestId(command.requestId());
        if(prior.isPresent())return replayOrConflict(
                prior.get(),adminId,productId,command);

        int available=adjustments.lockProductStock(productId)
                .orElseThrow(InventoryProductNotFound::new);

        // Reconsultar una vez adquirido el bloqueo: dos solicitudes con la
        // misma clave y producto nunca podrán aplicar dos ajustes.
        prior=adjustments.findByRequestId(command.requestId());
        if(prior.isPresent())return replayOrConflict(
                prior.get(),adminId,productId,command);

        int quantity=command.quantity();
        int changed;
        switch(command.kind()) {
            case ADJUSTMENT_IN -> {
                if(available>MAX_STOCK-quantity)
                    throw new InventoryAdjustmentConflict();
                changed=adjustments.addAvailable(productId,quantity);
            }
            case ADJUSTMENT_OUT -> {
                if(available<quantity)
                    throw new InventoryAdjustmentConflict();
                changed=adjustments.removeAvailable(productId,quantity);
            }
            default -> throw new IllegalArgumentException("Tipo no permitido");
        }
        if(changed!=1)throw new InventoryAdjustmentConflict();

        // Si falla este INSERT (incluida clave de idempotencia duplicada),
        // PostgreSQL revierte el UPDATE de stock en la misma transacción.
        adjustments.recordAdjustment(adminId,productId,command);
        var audit=adjustments.findByRequestId(command.requestId())
                .orElseThrow(IllegalStateException::new);
        var balance=balances.findBalance(productId)
                .orElseThrow(InventoryProductNotFound::new);
        return new InventoryAdjustmentResult(audit,balance,false);
    }

    private InventoryAdjustmentResult replayOrConflict(
            InventoryMovementAudit audit,long adminId,long productId,
            InventoryAdjustmentCommand command) {
        var movement=audit.movement();
        if(movement.productId()!=productId || audit.actorUserId()==null
                || audit.actorUserId()!=adminId
                || movement.kind()!=command.kind()
                || movement.quantity()!=command.quantity()
                || !Objects.equals(audit.reason(),command.reason())) {
            throw new InventoryAdjustmentConflict();
        }
        InventoryBalance current=balances.findBalance(productId)
                .orElseThrow(InventoryProductNotFound::new);
        return new InventoryAdjustmentResult(audit,current,true);
    }

    private static long offset(int page,int size) {
        if(page<0 || size<1 || size>100)
            throw new IllegalArgumentException("Paginación: page>=0 y size entre 1 y 100");
        return (long)page*size;
    }

    private static void positive(long id) {
        if(id<1)throw new IllegalArgumentException("El ID debe ser positivo");
    }
}
