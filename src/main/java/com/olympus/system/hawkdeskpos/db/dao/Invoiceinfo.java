package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Invoiceinfo — upgraded to Hibernate 6 / Jakarta Persistence 3.x
 */
@Entity
@Table(name = "invoiceinfo", catalog = "pharmacy")
public class Invoiceinfo implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoiceNo", nullable = false)
    private Integer invoiceNo;

    @Temporal(TemporalType.DATE)
    @Column(name = "date")
    private Date date;

    @Column(name = "total", precision = 22, scale = 0)
    private Double total;

    @Column(name = "stat", length = 45)
    private String stat;

    @Column(name = "paid", precision = 22, scale = 0)
    private Double paid;

    @Column(name = "discount", precision = 22, scale = 0)
    private Double discount;

    @OneToMany(mappedBy = "invoiceinfo", fetch = FetchType.LAZY)
    private Set<Invoice> invoices = new HashSet<>(0);

    @OneToMany(mappedBy = "invoiceinfo", fetch = FetchType.LAZY)
    private Set<Return> returns = new HashSet<>(0);

    // ── Constructors ────────────────────────────────────────────────────────
    public Invoiceinfo() {
    }

    public Invoiceinfo(Date date, Double total, String stat,
            Double paid, Double discount) {
        this.date = date;
        this.total = total;
        this.stat = stat;
        this.paid = paid;
        this.discount = discount;
    }

    public Invoiceinfo(Date date, Double total, String stat, Double paid,
            Double discount, Set<Invoice> invoices, Set<Return> returns) {
        this.date = date;
        this.total = total;
        this.stat = stat;
        this.paid = paid;
        this.discount = discount;
        this.invoices = invoices;
        this.returns = returns;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public Integer getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(Integer invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }

    public Double getPaid() {
        return paid;
    }

    public void setPaid(Double paid) {
        this.paid = paid;
    }

    public Double getDiscount() {
        return discount;
    }

    public void setDiscount(Double discount) {
        this.discount = discount;
    }

    public Set<Invoice> getInvoices() {
        return invoices;
    }

    public void setInvoices(Set<Invoice> invoices) {
        this.invoices = invoices;
    }

    public Set<Return> getReturns() {
        return returns;
    }

    public void setReturns(Set<Return> returns) {
        this.returns = returns;
    }
}
