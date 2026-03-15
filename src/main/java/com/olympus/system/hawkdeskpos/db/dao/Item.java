package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Item — upgraded to Hibernate 6 / Jakarta Persistence 3.x
 */
@Entity
@Table(name = "item", catalog = "pharmacy")
public class Item implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "itemId", nullable = false)
    private Integer itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catId", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brandId", nullable = false)
    private Brands brands;

    @Column(name = "itemName", length = 45)
    private String itemName;

    @Column(name = "minLevel")
    private Integer minLevel;

    @Column(name = "stat", length = 45)
    private String stat;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    private Set<Stock> stocks = new HashSet<>(0);

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    private Set<Invoice> invoices = new HashSet<>(0);

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    private Set<Grn> grns = new HashSet<>(0);

    // ── Constructors ────────────────────────────────────────────────────────
    public Item() {
    }

    public Item(Category category, Brands brands) {
        this.category = category;
        this.brands = brands;
    }

    public Item(Category category, Brands brands, String itemName,
            Integer minLevel, String stat,
            Set<Stock> stocks, Set<Invoice> invoices, Set<Grn> grns) {
        this.category = category;
        this.brands = brands;
        this.itemName = itemName;
        this.minLevel = minLevel;
        this.stat = stat;
        this.stocks = stocks;
        this.invoices = invoices;
        this.grns = grns;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Brands getBrands() {
        return brands;
    }

    public void setBrands(Brands brands) {
        this.brands = brands;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Integer getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(Integer minLevel) {
        this.minLevel = minLevel;
    }

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }

    public Set<Stock> getStocks() {
        return stocks;
    }

    public void setStocks(Set<Stock> stocks) {
        this.stocks = stocks;
    }

    public Set<Invoice> getInvoices() {
        return invoices;
    }

    public void setInvoices(Set<Invoice> invoices) {
        this.invoices = invoices;
    }

    public Set<Grn> getGrns() {
        return grns;
    }

    public void setGrns(Set<Grn> grns) {
        this.grns = grns;
    }
}
