package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "invoiceinfo")
public class Invoiceinfo implements Serializable {

    @Id
    @Column(name = "invoice_no", nullable = false, length = 20)
    private String invoiceNo;

    @Temporal(TemporalType.DATE)
    @Column(name = "date")
    private Date date;

    @Column(name = "total")
    private Double total;

    @Column(name = "stat", length = 45)
    private String stat = "Paid";

    @Column(name = "paid")
    private Double paid;

    @Column(name = "discount")
    private Double discount = 0.0;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod = "Cash";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @OneToMany(mappedBy = "invoiceinfo", fetch = FetchType.LAZY)
    private Set<Invoice> invoices = new HashSet<>(0);

    @OneToMany(mappedBy = "invoiceinfo", fetch = FetchType.LAZY)
    private Set<Return> returns = new HashSet<>(0);

    public Invoiceinfo() {}
    public Invoiceinfo(String invoiceNo, Date date, Double total, String stat, Double paid, Double discount) {
        this.invoiceNo = invoiceNo;
        this.date = date;
        this.total = total;
        this.stat = stat;
        this.paid = paid;
        this.discount = discount;
    }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
    public String getStat() { return stat; }
    public void setStat(String stat) { this.stat = stat; }
    public Double getPaid() { return paid; }
    public void setPaid(Double paid) { this.paid = paid; }
    public Double getDiscount() { return discount; }
    public void setDiscount(Double discount) { this.discount = discount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public Set<Invoice> getInvoices() { return invoices; }
    public void setInvoices(Set<Invoice> invoices) { this.invoices = invoices; }
    public Set<Return> getReturns() { return returns; }
    public void setReturns(Set<Return> returns) { this.returns = returns; }
}
