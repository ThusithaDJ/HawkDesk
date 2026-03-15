package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Invoice — upgraded to Hibernate 6 / Jakarta Persistence 3.x
 */
@Entity
@Table(name = "invoice", catalog = "pharmacy")
public class Invoice implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stockId", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itemId", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoiceNo", nullable = false)
    private Invoiceinfo invoiceinfo;

    @Column(name = "batch", length = 45)
    private String batch;

    @Temporal(TemporalType.DATE)
    @Column(name = "dateTime")
    private Date dateTime;

    @Column(name = "qty")
    private Integer qty;

    @Column(name = "subTotal", precision = 22, scale = 0)
    private Double subTotal;

    // ── Constructors ────────────────────────────────────────────────────────
    public Invoice() {
    }

    public Invoice(Stock stock, Item item, Invoiceinfo invoiceinfo) {
        this.stock = stock;
        this.item = item;
        this.invoiceinfo = invoiceinfo;
    }

    public Invoice(Stock stock, Item item, Invoiceinfo invoiceinfo,
            String batch, Date dateTime, Integer qty, Double subTotal) {
        this.stock = stock;
        this.item = item;
        this.invoiceinfo = invoiceinfo;
        this.batch = batch;
        this.dateTime = dateTime;
        this.qty = qty;
        this.subTotal = subTotal;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Stock getStock() {
        return stock;
    }

    public void setStock(Stock stock) {
        this.stock = stock;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Invoiceinfo getInvoiceinfo() {
        return invoiceinfo;
    }

    public void setInvoiceinfo(Invoiceinfo invoiceinfo) {
        this.invoiceinfo = invoiceinfo;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public Date getDateTime() {
        return dateTime;
    }

    public void setDateTime(Date dateTime) {
        this.dateTime = dateTime;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Double getSubTotal() {
        return subTotal;
    }

    public void setSubTotal(Double subTotal) {
        this.subTotal = subTotal;
    }
}
