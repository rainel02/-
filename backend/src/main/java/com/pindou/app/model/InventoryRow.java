package com.pindou.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_row")
public class InventoryRow {
    @Id
    private String code;
    private Integer inTotal;
    private Integer remain;
    private Integer used;
    private String warning;
    private Integer alertThreshold;

    public InventoryRow() {}

    public InventoryRow(String code, Integer inTotal, Integer remain, Integer used, String warning) {
        this.code = code;
        this.inTotal = inTotal;
        this.remain = remain;
        this.used = used;
        this.warning = warning;
    }

    public InventoryRow(String code, Integer inTotal, Integer remain, Integer used, String warning, Integer alertThreshold) {
        this.code = code;
        this.inTotal = inTotal;
        this.remain = remain;
        this.used = used;
        this.warning = warning;
        this.alertThreshold = alertThreshold;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getInTotal() { return inTotal; }
    public void setInTotal(Integer inTotal) { this.inTotal = inTotal; }
    public Integer getRemain() { return remain; }
    public void setRemain(Integer remain) { this.remain = remain; }
    public Integer getUsed() { return used; }
    public void setUsed(Integer used) { this.used = used; }
    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
    public Integer getAlertThreshold() { return alertThreshold; }
    public void setAlertThreshold(Integer alertThreshold) { this.alertThreshold = alertThreshold; }
}
