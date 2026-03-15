package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Return — upgraded to Hibernate 6 / Jakarta Persistence 3.x Note: 'return' is
 * a Java keyword so the class stays named Return, mapped to the 'return' table
 * via @Table.
 */
@Entity
@Table(name = "return", catalog = "pharmacy")
public class Return implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "returnId", nullable = false)
    private Integer returnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoiceNo", nullable = false)
    private Invoiceinfo invoiceinfo;

    // Note: original hbm.xml maps to column "Qty" (capital Q) — preserved exactly
    @Column(name = "Qty")
    private Integer qty;

    @Column(name = "reson", length = 45)
    private String reson;

    @Column(name = "returnTo")
    private Integer returnTo;

    @Column(name = "stat", length = 45)
    private String stat;

    // ── Constructors ────────────────────────────────────────────────────────
    public Return() {
    }

    public Return(Invoiceinfo invoiceinfo) {
        this.invoiceinfo = invoiceinfo;
    }

    public Return(Invoiceinfo invoiceinfo, Integer qty, String reson,
            Integer returnTo, String stat) {
        this.invoiceinfo = invoiceinfo;
        this.qty = qty;
        this.reson = reson;
        this.returnTo = returnTo;
        this.stat = stat;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public Integer getReturnId() {
        return returnId;
    }

    public void setReturnId(Integer returnId) {
        this.returnId = returnId;
    }

    public Invoiceinfo getInvoiceinfo() {
        return invoiceinfo;
    }

    public void setInvoiceinfo(Invoiceinfo invoiceinfo) {
        this.invoiceinfo = invoiceinfo;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public String getReson() {
        return reson;
    }

    public void setReson(String reson) {
        this.reson = reson;
    }

    public Integer getReturnTo() {
        return returnTo;
    }

    public void setReturnTo(Integer returnTo) {
        this.returnTo = returnTo;
    }

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }
}
