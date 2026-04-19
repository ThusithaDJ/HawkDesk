package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "cash_transaction")
public class CashTransaction implements Serializable {

    public enum TxType { SALE, GRN, RETURN_REFUND, INCOME, EXPENSE, CREDIT_PAYMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TxType type;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "amount", nullable = false, columnDefinition = "DECIMAL")
    private Double amount;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "transaction_date", nullable = false)
    private Date transactionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private CashAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "category", length = 100)
    private String category;

    // ── Nullable FK source references ─────────────────────────────────────────

    @Column(name = "invoice_no", length = 20)
    private String invoiceNoRef;

    @Column(name = "return_id")
    private Integer returnIdRef;

    @Column(name = "grn_no")
    private Integer grnNoRef;

    @Column(name = "income_id")
    private Long incomeIdRef;

    @Column(name = "expense_id")
    private Long expenseIdRef;

    public CashTransaction() {}

    public CashTransaction(TxType type, String reference, String description, double amount,
                           Date transactionDate, CashAccount account, Employee employee, String category) {
        this.type            = type;
        this.reference       = reference;
        this.description     = description;
        this.amount          = amount;
        this.transactionDate = transactionDate;
        this.account         = account;
        this.employee        = employee;
        this.category        = category;
    }

    public Long        getId()                              { return id; }
    public TxType      getType()                            { return type; }
    public void        setType(TxType type)                 { this.type = type; }
    public String      getReference()                       { return reference; }
    public void        setReference(String reference)       { this.reference = reference; }
    public String      getDescription()                     { return description; }
    public void        setDescription(String description)   { this.description = description; }
    public Double      getAmount()                          { return amount; }
    public void        setAmount(Double amount)             { this.amount = amount; }
    public Date        getTransactionDate()                 { return transactionDate; }
    public void        setTransactionDate(Date d)           { this.transactionDate = d; }
    public CashAccount getAccount()                         { return account; }
    public void        setAccount(CashAccount account)      { this.account = account; }
    public Employee    getEmployee()                        { return employee; }
    public void        setEmployee(Employee employee)       { this.employee = employee; }
    public String      getCategory()                        { return category; }
    public void        setCategory(String category)         { this.category = category; }
    public String      getInvoiceNoRef()                    { return invoiceNoRef; }
    public void        setInvoiceNoRef(String v)            { this.invoiceNoRef = v; }
    public Integer     getReturnIdRef()                     { return returnIdRef; }
    public void        setReturnIdRef(Integer v)            { this.returnIdRef = v; }
    public Integer     getGrnNoRef()                        { return grnNoRef; }
    public void        setGrnNoRef(Integer v)               { this.grnNoRef = v; }
    public Long        getIncomeIdRef()                     { return incomeIdRef; }
    public void        setIncomeIdRef(Long v)               { this.incomeIdRef = v; }
    public Long        getExpenseIdRef()                    { return expenseIdRef; }
    public void        setExpenseIdRef(Long v)              { this.expenseIdRef = v; }
}
