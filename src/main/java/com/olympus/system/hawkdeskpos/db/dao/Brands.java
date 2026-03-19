package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "brands")
public class Brands implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "brand_id", nullable = false)
    private Integer brandId;

    @Column(name = "brand_name", length = 45)
    private String brandName;

    @OneToMany(mappedBy = "brands", fetch = FetchType.LAZY)
    private Set<Item> items = new HashSet<>(0);

    public Brands() {}
    public Brands(String brandName) { this.brandName = brandName; }

    public Integer getBrandId() { return brandId; }
    public void setBrandId(Integer brandId) { this.brandId = brandId; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public Set<Item> getItems() { return items; }
    public void setItems(Set<Item> items) { this.items = items; }
}
