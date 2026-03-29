package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "invoice")
public class Invoice implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_no")
    private Invoiceinfo invoiceinfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "batch", length = 45)
    private String batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ItemBatch batchObj;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_time")
    private Date dateTime;

    @Column(name = "qty")
    private Integer qty;

    @Column(name = "sub_total")
    private Double subTotal;

    public Invoice() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Stock getStock() { return stock; }
    public void setStock(Stock stock) { this.stock = stock; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public Invoiceinfo getInvoiceinfo() { return invoiceinfo; }
    public void setInvoiceinfo(Invoiceinfo invoiceinfo) { this.invoiceinfo = invoiceinfo; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public ItemBatch getBatchObj() { return batchObj; }
    public void setBatchObj(ItemBatch batchObj) { this.batchObj = batchObj; }
    public Date getDateTime() { return dateTime; }
    public void setDateTime(Date dateTime) { this.dateTime = dateTime; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public Double getSubTotal() { return subTotal; }
    public void setSubTotal(Double subTotal) { this.subTotal = subTotal; }
}
