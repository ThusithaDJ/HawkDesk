package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_adjustment")
public class StockAdjustment {

    public enum AdjustmentType { ADD, REMOVE, SET, WRITEOFF }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ItemBatch batchObj;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false)
    private AdjustmentType adjustmentType;

    @Column(name = "qty_before", nullable = false)
    private int qtyBefore;

    @Column(name = "qty_change", nullable = false)
    private int qtyChange;

    @Column(name = "qty_after", nullable = false)
    private int qtyAfter;

    @Column(name = "reason", nullable = false, length = 128)
    private String reason;

    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "loss_value", precision = 12, scale = 2)
    private BigDecimal lossValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "adjusted_at", nullable = false)
    private LocalDateTime adjustedAt = LocalDateTime.now();

    public StockAdjustment() {}

    public Long getId() { return id; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public ItemBatch getBatchObj() { return batchObj; }
    public void setBatchObj(ItemBatch batchObj) { this.batchObj = batchObj; }
    public AdjustmentType getAdjustmentType() { return adjustmentType; }
    public void setAdjustmentType(AdjustmentType adjustmentType) { this.adjustmentType = adjustmentType; }
    public int getQtyBefore() { return qtyBefore; }
    public void setQtyBefore(int qtyBefore) { this.qtyBefore = qtyBefore; }
    public int getQtyChange() { return qtyChange; }
    public void setQtyChange(int qtyChange) { this.qtyChange = qtyChange; }
    public int getQtyAfter() { return qtyAfter; }
    public void setQtyAfter(int qtyAfter) { this.qtyAfter = qtyAfter; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getLossValue() { return lossValue; }
    public void setLossValue(BigDecimal lossValue) { this.lossValue = lossValue; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LocalDateTime getAdjustedAt() { return adjustedAt; }
    public void setAdjustedAt(LocalDateTime adjustedAt) { this.adjustedAt = adjustedAt; }
}
