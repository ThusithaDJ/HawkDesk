package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Grn — upgraded to Hibernate 6 / Jakarta Persistence 3.x
 */
@Entity
@Table(name = "grn", catalog = "pharmacy")
public class Grn implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "no", nullable = false)
    private Integer no;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itemId", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grnNo", nullable = false)
    private Grninfo grninfo;

    @Temporal(TemporalType.DATE)
    @Column(name = "expireDate")
    private Date expireDate;

    @Column(name = "itemQty")
    private Integer itemQty;

    @Column(name = "itemCost", precision = 22, scale = 0)
    private Double itemCost;

    @Column(name = "itemPrice", precision = 22, scale = 0)
    private Double itemPrice;

    // ── Constructors ────────────────────────────────────────────────────────
    public Grn() {
    }

    public Grn(Item item, Grninfo grninfo) {
        this.item = item;
        this.grninfo = grninfo;
    }

    public Grn(Item item, Grninfo grninfo, Date expireDate,
            Integer itemQty, Double itemCost, Double itemPrice) {
        this.item = item;
        this.grninfo = grninfo;
        this.expireDate = expireDate;
        this.itemQty = itemQty;
        this.itemCost = itemCost;
        this.itemPrice = itemPrice;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public Integer getNo() {
        return no;
    }

    public void setNo(Integer no) {
        this.no = no;
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

    public Date getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(Date expireDate) {
        this.expireDate = expireDate;
    }

    public Integer getItemQty() {
        return itemQty;
    }

    public void setItemQty(Integer itemQty) {
        this.itemQty = itemQty;
    }

    public Double getItemCost() {
        return itemCost;
    }

    public void setItemCost(Double itemCost) {
        this.itemCost = itemCost;
    }

    public Double getItemPrice() {
        return itemPrice;
    }

    public void setItemPrice(Double itemPrice) {
        this.itemPrice = itemPrice;
    }
}
