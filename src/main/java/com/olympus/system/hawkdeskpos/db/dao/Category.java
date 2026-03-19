package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "category")
public class Category implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cat_id", nullable = false)
    private Integer catId;

    @Column(name = "category_name", length = 45)
    private String categoryName;

    @Column(name = "stat", length = 45)
    private String stat;

    @Column(name = "colour", length = 10)
    private String colour = "#607D8B";

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private Set<Item> items = new HashSet<>(0);

    public Category() {}
    public Category(String categoryName, String stat) {
        this.categoryName = categoryName;
        this.stat = stat;
    }

    public Integer getCatId() { return catId; }
    public void setCatId(Integer catId) { this.catId = catId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getStat() { return stat; }
    public void setStat(String stat) { this.stat = stat; }
    public String getColour() { return colour; }
    public void setColour(String colour) { this.colour = colour; }
    public Set<Item> getItems() { return items; }
    public void setItems(Set<Item> items) { this.items = items; }
}
