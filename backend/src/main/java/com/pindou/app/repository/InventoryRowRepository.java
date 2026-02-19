package com.pindou.app.repository;

import com.pindou.app.model.InventoryRow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRowRepository extends JpaRepository<InventoryRow, String> {
}
