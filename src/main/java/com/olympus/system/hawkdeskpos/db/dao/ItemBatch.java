package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "item_batch")
public class ItemBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id", nullable = false)
    private Integer batchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grn_no")
    private Grninfo grninfo;

    @Column(name = "batch_number", length = 20, nullable = false, unique = true)
    private String batchNumber;

    @Column(name = "batch_label", length = 100)
    private String batchLabel;

    @Column(name = "qty_received", nullable = false)
    private int qtyReceived;

    @Column(name = "qty_remaining", nullable = false)
    private int qtyRemaining;

    @Column(name = "cost_price", nullable = false)
    private double costPrice;

    @Temporal(TemporalType.DATE)
    @Column(name = "expiry_date")
    private Date expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private BatchStatus status = BatchStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    public ItemBatch() {}

    public Integer getBatchId() { return batchId; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public Grninfo getGrninfo() { return grninfo; }
    public void setGrninfo(Grninfo grninfo) { this.grninfo = grninfo; }
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public String getBatchLabel() { return batchLabel; }
    public void setBatchLabel(String batchLabel) { this.batchLabel = batchLabel; }
    public int getQtyReceived() { return qtyReceived; }
    public void setQtyReceived(int qtyReceived) { this.qtyReceived = qtyReceived; }
    public int getQtyRemaining() { return qtyRemaining; }
    public void setQtyRemaining(int qtyRemaining) { this.qtyRemaining = qtyRemaining; }
    public double getCostPrice() { return costPrice; }
    public void setCostPrice(double costPrice) { this.costPrice = costPrice; }
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date expiryDate) { this.expiryDate = expiryDate; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Employee getCreatedBy() { return createdBy; }
    public void setCreatedBy(Employee createdBy) { this.createdBy = createdBy; }
}
