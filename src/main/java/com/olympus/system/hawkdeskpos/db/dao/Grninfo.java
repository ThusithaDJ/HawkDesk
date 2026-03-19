package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "grninfo")
public class Grninfo implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grn_no", nullable = false)
    private Integer grnNo;

    @Temporal(TemporalType.DATE)
    @Column(name = "date")
    private Date date;

    @Column(name = "sub_total")
    private Double subTotal;

    @Column(name = "supplier", length = 128)
    private String supplier;

    @Column(name = "reference", length = 64)
    private String reference;

    @OneToMany(mappedBy = "grninfo", fetch = FetchType.LAZY)
    private Set<Stock> stocks = new HashSet<>(0);

    @OneToMany(mappedBy = "grninfo", fetch = FetchType.LAZY)
    private Set<Grn> grns = new HashSet<>(0);

    public Grninfo() {}
    public Grninfo(Date date, Double subTotal) { this.date = date; this.subTotal = subTotal; }

    public Integer getGrnNo() { return grnNo; }
    public void setGrnNo(Integer grnNo) { this.grnNo = grnNo; }
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
    public Double getSubTotal() { return subTotal; }
    public void setSubTotal(Double subTotal) { this.subTotal = subTotal; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Set<Stock> getStocks() { return stocks; }
    public void setStocks(Set<Stock> stocks) { this.stocks = stocks; }
    public Set<Grn> getGrns() { return grns; }
    public void setGrns(Set<Grn> grns) { this.grns = grns; }
}
