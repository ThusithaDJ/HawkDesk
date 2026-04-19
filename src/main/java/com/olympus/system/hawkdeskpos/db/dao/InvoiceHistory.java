package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "invoice_history")
public class InvoiceHistory implements Serializable {

    public enum EventType {
        CREATED, PAID, VOIDED, RETURNED, PARTIAL_RETURN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "invoice_no", nullable = false, length = 50)
    private String invoiceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "event_date", nullable = false)
    private Date eventDate;

    @Column(name = "amount", columnDefinition = "DECIMAL")
    private Double amount;

    @Column(name = "notes", length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    public InvoiceHistory() {}

    public InvoiceHistory(String invoiceNo, EventType eventType, Date eventDate,
                          Double amount, String notes, Employee employee) {
        this.invoiceNo  = invoiceNo;
        this.eventType  = eventType;
        this.eventDate  = eventDate;
        this.amount     = amount;
        this.notes      = notes;
        this.employee   = employee;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public Date getEventDate() { return eventDate; }
    public void setEventDate(Date eventDate) { this.eventDate = eventDate; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
}
