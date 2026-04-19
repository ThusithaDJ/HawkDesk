package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "expense")
public class Expense implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private CashAccount account;

    @Column(name = "amount", nullable = false, columnDefinition = "DECIMAL")
    private Double amount;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "reference", length = 100)
    private String reference;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "transaction_date", nullable = false)
    private Date transactionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    public Expense() {}

    public Expense(CashAccount account, double amount, String description,
                   String reference, Date transactionDate, Employee employee) {
        this.account         = account;
        this.amount          = amount;
        this.description     = description;
        this.reference       = reference;
        this.transactionDate = transactionDate;
        this.employee        = employee;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public CashAccount getAccount() { return account; }
    public void setAccount(CashAccount account) { this.account = account; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public Date getTransactionDate() { return transactionDate; }
    public void setTransactionDate(Date transactionDate) { this.transactionDate = transactionDate; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
}
