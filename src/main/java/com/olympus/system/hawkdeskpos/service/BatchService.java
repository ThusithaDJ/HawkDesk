package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.*;
import com.olympus.system.hawkdeskpos.dto.BatchDto;
import com.olympus.system.hawkdeskpos.dto.BatchDeductionResult;
import com.olympus.system.hawkdeskpos.dto.ItemWithBatchesDto;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class BatchService {

    private final SessionFactory sf;

    public BatchService(SessionFactory sf) {
        this.sf = sf;
    }

    // ── Batch number generation ───────────────────────────────────────────────

    /**
     * Generates the next batch number using the default pattern "B{YYYY}-{NNN}".
     * Delegates to {@link #generateBatchNumber(String)}.
     */
    public String generateBatchNumber() {
        return generateBatchNumber("B{YYYY}-{NNN}");
    }

    /**
     * Generates the next batch number using a configurable pattern.
     * Supported placeholders: {@code {YYYY}} = current year, {@code {NNN}} = zero-padded sequence.
     * Example: {@code "B{YYYY}-{NNN}"} → {@code "B2026-001"}.
     */
    public String generateBatchNumber(String pattern) {
        String year     = String.valueOf(LocalDate.now().getYear());
        String resolved = pattern.replace("{YYYY}", year);
        String prefix   = resolved.replace("{NNN}", "");
        try (var session = sf.openSession()) {
            Long count = session.createQuery(
                    "SELECT COUNT(b) FROM ItemBatch b WHERE b.batchNumber LIKE :p", Long.class)
                    .setParameter("p", prefix + "%").uniqueResult();
            return resolved.replace("{NNN}", String.format("%03d", (count == null ? 0 : count) + 1));
        }
    }

    // ── Batch creation ────────────────────────────────────────────────────────

    /**
     * Creates a new ItemBatch and returns it (must be called within an open session/tx from caller).
     * Used by StockService.saveGrn() so the batch is linked to the stock record in the same tx.
     */
    public ItemBatch createBatch(Session session, Item item, Grninfo grninfo,
                                  String batchNumber, String batchLabel,
                                  int qtyReceived, double costPrice,
                                  Date expiryDate, Employee createdBy) {
        ItemBatch batch = new ItemBatch();
        batch.setItem(item);
        batch.setGrninfo(grninfo);
        batch.setBatchNumber(batchNumber);
        batch.setBatchLabel(batchLabel);
        batch.setQtyReceived(qtyReceived);
        batch.setQtyRemaining(qtyReceived);
        batch.setCostPrice(costPrice);
        batch.setExpiryDate(expiryDate);
        batch.setStatus(BatchStatus.ACTIVE);
        batch.setCreatedBy(createdBy);
        session.persist(batch);
        return batch;
    }

    // ── FIFO deduction ────────────────────────────────────────────────────────

    /**
     * Deducts qty from the oldest ACTIVE batch for an item (FIFO, pessimistic lock).
     * Also updates stock.qty on the linked stock record.
     * Returns which batch was used and how much was deducted.
     * Throws InsufficientBatchStockException if total available < qtyNeeded.
     */
    public BatchDeductionResult deductFifo(Session session, int itemId, int qtyNeeded) {
        List<ItemBatch> batches = session.createQuery(
                "FROM ItemBatch b WHERE b.item.itemId = :id AND b.status = 'ACTIVE' " +
                "AND b.qtyRemaining > 0 ORDER BY b.batchId ASC",
                ItemBatch.class)
                .setParameter("id", itemId)
                .list();

        int totalAvailable = batches.stream().mapToInt(ItemBatch::getQtyRemaining).sum();
        if (totalAvailable < qtyNeeded) {
            throw new InsufficientBatchStockException(itemId, qtyNeeded, totalAvailable);
        }

        // Take from the first batch (FIFO)
        ItemBatch fifo = batches.get(0);
        int deduct = Math.min(qtyNeeded, fifo.getQtyRemaining());
        fifo.setQtyRemaining(fifo.getQtyRemaining() - deduct);
        if (fifo.getQtyRemaining() == 0) {
            fifo.setStatus(BatchStatus.EMPTY);
        }
        session.merge(fifo);
        return new BatchDeductionResult(fifo.getBatchId(), fifo.getBatchNumber(), deduct, fifo.getCostPrice());
    }

    // ── Expiry management ─────────────────────────────────────────────────────

    /** Marks any ACTIVE batches whose expiry_date < today as EXPIRED. */
    public int markExpiredBatches() {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            List<ItemBatch> expired = session.createQuery(
                    "FROM ItemBatch b WHERE b.status = 'ACTIVE' AND b.expiryDate IS NOT NULL " +
                    "AND b.expiryDate < :today",
                    ItemBatch.class)
                    .setParameter("today", Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()))
                    .list();
            for (ItemBatch b : expired) {
                b.setStatus(BatchStatus.EXPIRED);
                session.merge(b);
            }
            tx.commit();
            return expired.size();
        }
    }

    /** Returns batches expiring within the given number of days (status = ACTIVE). */
    public List<BatchDto> getExpiringBatches(int daysAhead) {
        Date cutoff = Date.from(LocalDate.now().plusDays(daysAhead)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "SELECT b FROM ItemBatch b JOIN FETCH b.item " +
                    "WHERE b.status = 'ACTIVE' AND b.expiryDate IS NOT NULL AND b.expiryDate <= :cutoff " +
                    "ORDER BY b.expiryDate ASC",
                    ItemBatch.class)
                    .setParameter("cutoff", cutoff)
                    .list().stream().map(this::toDto).collect(Collectors.toList());
        }
    }

    // ── Query methods ─────────────────────────────────────────────────────────

    public List<BatchDto> listBatchesForItem(int itemId) {
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "FROM ItemBatch b WHERE b.item.itemId = :id ORDER BY b.batchId ASC",
                    ItemBatch.class)
                    .setParameter("id", itemId)
                    .list().stream().map(this::toDto).collect(Collectors.toList());
        }
    }

    public List<BatchDto> listActiveBatchesForItem(int itemId) {
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "FROM ItemBatch b WHERE b.item.itemId = :id AND b.status = 'ACTIVE' " +
                    "ORDER BY b.batchId ASC",
                    ItemBatch.class)
                    .setParameter("id", itemId)
                    .list().stream().map(this::toDto).collect(Collectors.toList());
        }
    }

    public List<ItemWithBatchesDto> listAllItemsWithBatches() {
        try (var session = sf.openSession()) {
            List<ItemBatch> batches = session.createQuery(
                    "SELECT b FROM ItemBatch b JOIN FETCH b.item i " +
                    "WHERE b.status = 'ACTIVE' ORDER BY i.itemName, b.batchId",
                    ItemBatch.class).list();

            Map<Integer, List<ItemBatch>> byItem = batches.stream()
                    .collect(Collectors.groupingBy(b -> b.getItem().getItemId()));

            return byItem.entrySet().stream().map(e -> {
                Item item = e.getValue().get(0).getItem();
                int total = e.getValue().stream().mapToInt(ItemBatch::getQtyRemaining).sum();
                List<BatchDto> dtos = e.getValue().stream().map(this::toDto).collect(Collectors.toList());
                return new ItemWithBatchesDto(
                        item.getItemId(), item.getItemName(),
                        item.getSku() != null ? item.getSku() : "",
                        total, dtos);
            }).sorted(Comparator.comparing(ItemWithBatchesDto::itemName)).collect(Collectors.toList());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BatchDto toDto(ItemBatch b) {
        return new BatchDto(
                b.getBatchId(),
                b.getItem().getItemId(),
                b.getItem().getItemName(),
                b.getBatchNumber(),
                b.getBatchLabel(),
                b.getQtyReceived(),
                b.getQtyRemaining(),
                b.getCostPrice(),
                b.getExpiryDate(),
                b.getStatus().name());
    }
}
