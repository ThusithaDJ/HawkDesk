package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "item")
public class Item implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", nullable = false)
    private Integer itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brands brands;

    @Column(name = "item_name", length = 45)
    private String itemName;

    @Column(name = "sku", length = 45, unique = true)
    private String sku;

    @Column(name = "min_level")
    private Integer minLevel = 5;

    @Column(name = "max_level")
    private Integer maxLevel = 100;

    @Column(name = "sec_unit", length = 20)
    private String secUnit = "pcs";

    @Column(name = "sell_unit", length = 20)
    private String sellUnit;

    @Column(name = "conversion_factor", columnDefinition = "DECIMAL")
    private Double conversionFactor = 1.0;

    @Column(name = "stat", length = 45)
    private String stat = "Active";

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    private Set<Stock> stocks = new HashSet<>(0);

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    private Set<ItemVariant> variants = new HashSet<>(0);

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    private Set<Invoice> invoices = new HashSet<>(0);

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    private Set<Grn> grns = new HashSet<>(0);

    public Item() {}
    public Item(Category category, Brands brands) {
        this.category = category;
        this.brands = brands;
    }

    public Integer getItemId() { return itemId; }
    public void setItemId(Integer itemId) { this.itemId = itemId; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Brands getBrands() { return brands; }
    public void setBrands(Brands brands) { this.brands = brands; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Integer getMinLevel() { return minLevel; }
    public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }
    public Integer getMaxLevel() { return maxLevel; }
    public void setMaxLevel(Integer maxLevel) { this.maxLevel = maxLevel; }
    public String getSecUnit() { return secUnit; }
    public void setSecUnit(String secUnit) { this.secUnit = secUnit; }
    public String getSellUnit() { return sellUnit; }
    public void setSellUnit(String sellUnit) { this.sellUnit = sellUnit; }
    public Double getConversionFactor() { return conversionFactor; }
    public void setConversionFactor(Double conversionFactor) { this.conversionFactor = conversionFactor; }
    public String getStat() { return stat; }
    public void setStat(String stat) { this.stat = stat; }
    public Set<Stock> getStocks() { return stocks; }
    public void setStocks(Set<Stock> stocks) { this.stocks = stocks; }
    public Set<ItemVariant> getVariants() { return variants; }
    public void setVariants(Set<ItemVariant> variants) { this.variants = variants; }
    public Set<Invoice> getInvoices() { return invoices; }
    public void setInvoices(Set<Invoice> invoices) { this.invoices = invoices; }
    public Set<Grn> getGrns() { return grns; }
    public void setGrns(Set<Grn> grns) { this.grns = grns; }
}
