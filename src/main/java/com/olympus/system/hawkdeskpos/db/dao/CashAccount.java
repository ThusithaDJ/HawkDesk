package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "cash_account")
public class CashAccount implements Serializable {

    public enum AccountType { CASH_DRAWER, SAVINGS, CURRENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "balance", nullable = false, columnDefinition = "DECIMAL")
    private Double balance = 0.0;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    public CashAccount() {}

    public CashAccount(String name, AccountType accountType) {
        this.name        = name;
        this.accountType = accountType;
        this.balance     = 0.0;
        this.createdAt   = new Date();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public Double getBalance() { return balance != null ? balance : 0.0; }
    public void setBalance(Double balance) { this.balance = balance; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    /** Display label combining account type badge and name. */
    public String typeLabel() {
        return switch (accountType) {
            case CASH_DRAWER -> "Cash Drawer";
            case SAVINGS     -> "Savings";
            case CURRENT     -> "Current";
        };
    }
}
