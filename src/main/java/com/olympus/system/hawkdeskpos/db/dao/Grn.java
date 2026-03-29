package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "grn")
public class Grn implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "no", nullable = false)
    private Integer no;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ItemBatch batchObj;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grn_no")
    private Grninfo grninfo;

    @Temporal(TemporalType.DATE)
    @Column(name = "expire_date")
    private Date expireDate;

    @Column(name = "item_qty")
    private Integer itemQty;

    @Column(name = "item_cost")
    private Double itemCost;

    @Column(name = "item_price")
    private Double itemPrice;

    public Grn() {}
    public Grn(Item item, Grninfo grninfo) { this.item = item; this.grninfo = grninfo; }

    public Integer getNo() { return no; }
    public void setNo(Integer no) { this.no = no; }
    public ItemBatch getBatchObj() { return batchObj; }
    public void setBatchObj(ItemBatch batchObj) { this.batchObj = batchObj; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public Grninfo getGrninfo() { return grninfo; }
    public void setGrninfo(Grninfo grninfo) { this.grninfo = grninfo; }
    public Date getExpireDate() { return expireDate; }
    public void setExpireDate(Date expireDate) { this.expireDate = expireDate; }
    public Integer getItemQty() { return itemQty; }
    public void setItemQty(Integer itemQty) { this.itemQty = itemQty; }
    public Double getItemCost() { return itemCost; }
    public void setItemCost(Double itemCost) { this.itemCost = itemCost; }
    public Double getItemPrice() { return itemPrice; }
    public void setItemPrice(Double itemPrice) { this.itemPrice = itemPrice; }
}
