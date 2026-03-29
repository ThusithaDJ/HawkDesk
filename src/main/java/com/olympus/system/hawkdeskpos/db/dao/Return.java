package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "`return`")
public class Return implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_id", nullable = false)
    private Integer returnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_no")
    private Invoiceinfo invoiceinfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "qty")
    private Integer qty;

    @Column(name = "reason", length = 128)
    private String reason;

    @Column(name = "return_to", length = 45)
    private String returnTo;

    @Column(name = "stat", length = 45)
    private String stat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(name = "item_name", length = 100)
    private String itemName;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "return_date")
    private Date returnDate;

    public Return() {}

    public Integer getReturnId()                    { return returnId; }
    public void    setReturnId(Integer returnId)    { this.returnId = returnId; }
    public Invoiceinfo getInvoiceinfo()             { return invoiceinfo; }
    public void    setInvoiceinfo(Invoiceinfo ii)   { this.invoiceinfo = ii; }
    public Employee getEmployee()                   { return employee; }
    public void    setEmployee(Employee employee)   { this.employee = employee; }
    public Integer getQty()                         { return qty; }
    public void    setQty(Integer qty)              { this.qty = qty; }
    public String  getReason()                      { return reason; }
    public void    setReason(String reason)         { this.reason = reason; }
    public String  getReturnTo()                    { return returnTo; }
    public void    setReturnTo(String returnTo)     { this.returnTo = returnTo; }
    public String  getStat()                        { return stat; }
    public void    setStat(String stat)             { this.stat = stat; }
    public Item    getItem()                        { return item; }
    public void    setItem(Item item)               { this.item = item; }
    public Stock   getStock()                       { return stock; }
    public void    setStock(Stock stock)            { this.stock = stock; }
    public String  getItemName()                    { return itemName; }
    public void    setItemName(String itemName)     { this.itemName = itemName; }
    public Date    getReturnDate()                  { return returnDate; }
    public void    setReturnDate(Date returnDate)   { this.returnDate = returnDate; }
}
