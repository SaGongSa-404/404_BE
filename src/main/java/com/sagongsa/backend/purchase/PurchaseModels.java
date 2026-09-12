package com.sagongsa.backend.purchase;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PurchaseModels {
  private PurchaseModels() {}

  public enum Status {
    CONSIDERING,
    PURCHASED,
    DECLINED
  }

  public record Change(
      UUID mutationId,
      Long expectedRevision,
      Status status,
      String note,
      Integer actualPrice,
      LocalDate purchasedOn) {}

  public record Item(
      UUID id,
      String title,
      String imageUrl,
      String originalUrl,
      Integer listedPrice,
      String currencyCode,
      String category,
      String status,
      String note,
      Integer actualPrice,
      LocalDate purchasedOn,
      long revision,
      boolean legacyDecision) {}

  public record Page(List<Item> items, Integer nextOffset) {}

  public record Availability(boolean enabled, boolean hasRecords) {}
}
