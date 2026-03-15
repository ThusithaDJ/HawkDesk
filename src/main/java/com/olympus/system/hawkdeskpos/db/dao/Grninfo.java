package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Grninfo — upgraded to Hibernate 6 / Jakarta Persistence 3.x
 */
@Entity
@Table(name = "grninfo", catalog = "pharmacy")
public class Grninfo implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grnNo", nullable = false)
    private Integer grnNo;

    @Temporal(TemporalType.DATE)
    @Column(name = "date")
    private Date date;

    @Column(name = "subTotal", precision = 22, scale = 0)
    private Double subTotal;

    @OneToMany(mappedBy = "grninfo", fetch = FetchType.LAZY)
    private Set<Stock> stocks = new HashSet<>(0);

    @OneToMany(mappedBy = "grninfo", fetch = FetchType.LAZY)
    private Set<Grn> grns = new HashSet<>(0);

    // ── Constructors ────────────────────────────────────────────────────────
    public Grninfo() {
    }

    public Grninfo(Date date, Double subTotal) {
        this.date = date;
        this.subTotal = subTotal;
    }

    public Grninfo(Date date, Double subTotal, Set<Stock> stocks, Set<Grn> grns) {
        this.date = date;
        this.subTotal = subTotal;
        this.stocks = stocks;
        this.grns = grns;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public Integer getGrnNo() {
        return grnNo;
    }

    public void setGrnNo(Integer grnNo) {
        this.grnNo = grnNo;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Double getSubTotal() {
        return subTotal;
    }

    public void setSubTotal(Double subTotal) {
        this.subTotal = subTotal;
    }

    public Set<Stock> getStocks() {
        return stocks;
    }

    public void setStocks(Set<Stock> stocks) {
        this.stocks = stocks;
    }

    public Set<Grn> getGrns() {
        return grns;
    }

    public void setGrns(Set<Grn> grns) {
        this.grns = grns;
    }
}
