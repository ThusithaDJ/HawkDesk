package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer")
public class Customer implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", length = 256)
    private String address;

    @Column(name = "max_debt_amount", columnDefinition = "DECIMAL")
    private Double maxDebtAmount = 0.0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Customer() {}

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getMaxDebtAmount() { return maxDebtAmount; }
    public void setMaxDebtAmount(Double maxDebtAmount) { this.maxDebtAmount = maxDebtAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
