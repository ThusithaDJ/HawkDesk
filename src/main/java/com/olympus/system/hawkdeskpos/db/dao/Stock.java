package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "stock")
public class Stock implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id", nullable = false)
    private Integer stockId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grn_no")
    private Grninfo grninfo;

    @Column(name = "batch", length = 45)
    private String batch;

    @Column(name = "sku", length = 45)
    private String sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ItemVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ItemBatch batchObj;

    @Temporal(TemporalType.DATE)
    @Column(name = "expire_date")
    private Date expireDate;

    @Column(name = "qty", columnDefinition = "DECIMAL")
    private Double qty;

    @Column(name = "cost", columnDefinition = "DECIMAL")
    private Double cost;

    @Column(name = "price", columnDefinition = "DECIMAL")
    private Double price;

    @Column(name = "stat", length = 45)
    private String stat = "Active";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @OneToMany(mappedBy = "stock", fetch = FetchType.LAZY)
    private Set<Invoice> invoices = new HashSet<>(0);

    public Stock() {}

    public Integer getStockId() { return stockId; }
    public void setStockId(Integer stockId) { this.stockId = stockId; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public Grninfo getGrninfo() { return grninfo; }
    public void setGrninfo(Grninfo grninfo) { this.grninfo = grninfo; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public ItemVariant getVariant() { return variant; }
    public void setVariant(ItemVariant variant) { this.variant = variant; }
    public ItemBatch getBatchObj() { return batchObj; }
    public void setBatchObj(ItemBatch batchObj) { this.batchObj = batchObj; }
    public Date getExpireDate() { return expireDate; }
    public void setExpireDate(Date expireDate) { this.expireDate = expireDate; }
    public Double getQty() { return qty; }
    public void setQty(Double qty) { this.qty = qty; }
    public Double getCost() { return cost; }
    public void setCost(Double cost) { this.cost = cost; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getStat() { return stat; }
    public void setStat(String stat) { this.stat = stat; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public Set<Invoice> getInvoices() { return invoices; }
    public void setInvoices(Set<Invoice> invoices) { this.invoices = invoices; }
}
