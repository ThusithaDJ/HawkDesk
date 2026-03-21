package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * A named price/cost variant of a parent Item.
 * Created when a GRN arrives with a different cost/price and the user assigns a new SKU.
 * Multiple Stock records can reference the same variant_id.
 */
@Entity
@Table(name = "item_variant")
public class ItemVariant implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variant_id", nullable = false)
    private Integer variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "sku", length = 45, nullable = false, unique = true)
    private String sku;

    @Column(name = "stat", length = 45)
    private String stat = "Active";

    public ItemVariant() {}

    public Integer getVariantId() { return variantId; }
    public void setVariantId(Integer variantId) { this.variantId = variantId; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getStat() { return stat; }
    public void setStat(String stat) { this.stat = stat; }
}
