package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Category — upgraded to Hibernate 6 / Jakarta Persistence 3.x
 */
@Entity
@Table(name = "category", catalog = "pharmacy")
public class Category implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "catId", nullable = false)
    private Integer catId;

    @Column(name = "categoryName", length = 45)
    private String categoryName;

    @Column(name = "stat", length = 45)
    private String stat;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private Set<Item> items = new HashSet<>(0);

    // ── Constructors ────────────────────────────────────────────────────────
    public Category() {
    }

    public Category(String categoryName, String stat) {
        this.categoryName = categoryName;
        this.stat = stat;
    }

    public Category(String categoryName, String stat, Set<Item> items) {
        this.categoryName = categoryName;
        this.stat = stat;
        this.items = items;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public Integer getCatId() {
        return catId;
    }

    public void setCatId(Integer catId) {
        this.catId = catId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }

    public Set<Item> getItems() {
        return items;
    }

    public void setItems(Set<Item> items) {
        this.items = items;
    }
}
