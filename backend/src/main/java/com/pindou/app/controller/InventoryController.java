package com.pindou.app.controller;

import com.pindou.app.model.DemandRow;
import com.pindou.app.model.InventoryRow;
import com.pindou.app.model.TodoProjectRow;
import com.pindou.app.model.UsageRow;
import com.pindou.app.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/stock")
    public List<InventoryRow> stock() {
        return inventoryService.stock();
    }

    @GetMapping("/usage")
    public List<UsageRow> usage() {
        return inventoryService.usage();
    }

    @GetMapping("/demand")
    public List<DemandRow> demand() {
        return inventoryService.demand();
    }

    @GetMapping("/todo-projects")
    public List<TodoProjectRow> todoProjects() {
        return inventoryService.todoProjects();
    }

    @PostMapping("/restock")
    public List<InventoryRow> restock(@RequestBody Map<String, Integer> request) {
        return inventoryService.restock(request);
    }

    @PatchMapping("/thresholds")
    public List<InventoryRow> updateThresholds(@RequestBody Map<String, Integer> request) {
        return inventoryService.updateThresholds(request);
    }

    @PatchMapping("/in-totals")
    public List<InventoryRow> updateInTotals(@RequestBody Map<String, Integer> request) {
        return inventoryService.updateInTotals(request);
    }
}
