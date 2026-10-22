package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "uom_preset")
public class UomPreset implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "secondary_unit", length = 20)
    private String secondaryUnit;

    @Column(name = "conversion_factor", columnDefinition = "DECIMAL")
    private Double conversionFactor = 1.0;

    public UomPreset() {}

    public Integer getId()                          { return id; }
    public void    setId(Integer id)                { this.id = id; }
    public String  getName()                        { return name; }
    public void    setName(String n)                { this.name = n; }
    public String  getSecondaryUnit()               { return secondaryUnit; }
    public void    setSecondaryUnit(String u)       { this.secondaryUnit = u; }
    public Double  getConversionFactor()            { return conversionFactor; }
    public void    setConversionFactor(Double f)    { this.conversionFactor = f; }

    @Override public String toString() { return name; }
}
