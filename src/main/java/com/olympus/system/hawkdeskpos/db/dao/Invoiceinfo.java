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

    @Column(name = "net_total", columnDefinition = "DECIMAL")
    private Double netTotal;

    @Column(name = "sub_total", columnDefinition = "DECIMAL")
    private Double subTotal;

    @Column(name = "gross_total", columnDefinition = "DECIMAL")
    private Double grossTotal;

    @Column(name = "tax", columnDefinition = "DECIMAL")
    private Double tax;

    @Column(name = "stat", length = 45)
    private String stat = "Paid";

    @Column(name = "paid", columnDefinition = "DECIMAL")
    private Double paid;

    @Column(name = "discount", columnDefinition = "DECIMAL")
    private Double discount = 0.0;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod = "Cash";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Temporal(TemporalType.DATE)
    @Column(name = "credit_resolve_date")
    private Date creditResolveDate;

    @OneToMany(mappedBy = "invoiceinfo", fetch = FetchType.LAZY)
    private Set<Invoice> invoices = new HashSet<>(0);

    @OneToMany(mappedBy = "invoiceinfo", fetch = FetchType.LAZY)
    private Set<Return> returns = new HashSet<>(0);

    public Invoiceinfo() {}
    public Invoiceinfo(String invoiceNo, Date date, Double netTotal, String stat, Double paid, Double discount) {
        this.invoiceNo = invoiceNo;
        this.date = date;
        this.netTotal = netTotal;
        this.stat = stat;
        this.paid = paid;
        this.discount = discount;
    }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
    public Double getNetTotal() { return netTotal; }
    public void setNetTotal(Double netTotal) { this.netTotal = netTotal; }
    public Double getSubTotal() { return subTotal; }
    public void setSubTotal(Double subTotal) { this.subTotal = subTotal; }
    public Double getGrossTotal() { return grossTotal; }
    public void setGrossTotal(Double grossTotal) { this.grossTotal = grossTotal; }
    public Double getTax() { return tax; }
    public void setTax(Double tax) { this.tax = tax; }
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
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Date getCreditResolveDate() { return creditResolveDate; }
    public void setCreditResolveDate(Date creditResolveDate) { this.creditResolveDate = creditResolveDate; }
    public Set<Invoice> getInvoices() { return invoices; }
    public void setInvoices(Set<Invoice> invoices) { this.invoices = invoices; }
    public Set<Return> getReturns() { return returns; }
    public void setReturns(Set<Return> returns) { this.returns = returns; }
}
