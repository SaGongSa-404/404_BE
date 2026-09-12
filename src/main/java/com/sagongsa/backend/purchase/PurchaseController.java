package com.sagongsa.backend.purchase;

import static com.sagongsa.backend.purchase.PurchaseModels.*;

import com.sagongsa.backend.auth.CurrentUserId;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchase-items")
public class PurchaseController {
  private final PurchaseService service;

  public PurchaseController(PurchaseService service) {
    this.service = service;
  }

  @GetMapping("/availability")
  public Availability availability(@CurrentUserId UUID userId) {
    return service.availability(userId);
  }

  @GetMapping
  public Page list(
      @CurrentUserId UUID userId,
      @RequestParam(defaultValue = "false") boolean records,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "50") int limit) {
    return service.list(userId, records, offset, limit);
  }

  @GetMapping("/{itemId}")
  public Item get(@CurrentUserId UUID userId, @PathVariable UUID itemId) {
    return service.get(userId, itemId);
  }

  @PutMapping("/{itemId}")
  public Item change(
      @CurrentUserId UUID userId, @PathVariable UUID itemId, @RequestBody Change request) {
    return service.change(userId, itemId, request);
  }
}
