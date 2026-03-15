package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Stock — upgraded to Hibernate 6 / Jakarta Persistence 3.x
 */
@Entity
@Table(name = "stock", catalog = "pharmacy")
public class Stock implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stockId", nullable = false)
    private Integer stockId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itemId", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grnNo", nullable = false)
    private Grninfo grninfo;

    @Column(name = "batch", length = 45)
    private String batch;

    @Temporal(TemporalType.DATE)
    @Column(name = "expireDate")
    private Date expireDate;

    @Column(name = "qty")
    private Integer qty;

    @Column(name = "cost", precision = 22, scale = 0)
    private Double cost;

    @Column(name = "price", precision = 22, scale = 0)
    private Double price;

    @Column(name = "stat", length = 45)
    private String stat;

    @OneToMany(mappedBy = "stock", fetch = FetchType.LAZY)
    private Set<Invoice> invoices = new HashSet<>(0);

    // ── Constructors ────────────────────────────────────────────────────────
    public Stock() {
    }

    public Stock(Item item, Grninfo grninfo) {
        this.item = item;
        this.grninfo = grninfo;
    }

    public Stock(Item item, Grninfo grninfo, String batch, Date expireDate,
            Integer qty, Double cost, Double price, String stat,
            Set<Invoice> invoices) {
        this.item = item;
        this.grninfo = grninfo;
        this.batch = batch;
        this.expireDate = expireDate;
        this.qty = qty;
        this.cost = cost;
        this.price = price;
        this.stat = stat;
        this.invoices = invoices;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public Integer getStockId() {
        return stockId;
    }

    public void setStockId(Integer stockId) {
        this.stockId = stockId;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Grninfo getGrninfo() {
        return grninfo;
    }

    public void setGrninfo(Grninfo grninfo) {
        this.grninfo = grninfo;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public Date getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(Date expireDate) {
        this.expireDate = expireDate;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }

    public Set<Invoice> getInvoices() {
        return invoices;
    }

    public void setInvoices(Set<Invoice> invoices) {
        this.invoices = invoices;
    }
}
