package edu.cit.manubag.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Package-private repository. Nothing outside edu.cit.manubag.inventory
 * touches persistence directly — everything goes through InventoryService.
 */
interface InventoryRepository extends JpaRepository<Inventory, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.productId = :productId")
    Optional<Inventory> lockByProductId(String productId);
}
