package com.sagongsa.backend.purchase;

import static com.sagongsa.backend.purchase.PurchaseModels.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.budget.BudgetCycleRolloverService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PurchaseService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Clock clock;
  private final BudgetCycleRolloverService rollover;
  private final boolean enabled;
  private final Set<String> allowedUsers;
  private static final String SELECT =
      """
select si.id, si.title, si.image_url, si.original_url, si.listed_price, si.currency_code, si.category,
  coalesce(po.status, case when pd.id is not null then 'LEGACY_' || pd.result else 'CONSIDERING' end) as outcome_status,
  coalesce(po.note, '') as note, po.actual_price, po.purchased_on, coalesce(po.revision,0) as revision,
  pd.id is not null as legacy_decision
from saved_items si left join purchase_outcomes po on po.item_id=si.id
left join purchase_decisions pd on pd.item_id=si.id
""";

  public PurchaseService(
      JdbcTemplate jdbc,
      ObjectMapper json,
      Clock clock,
      BudgetCycleRolloverService rollover,
      @Value("${app.purchase-comparison.enabled:false}") boolean enabled,
      @Value("${app.purchase-comparison.allowed-user-ids:}") String allowedUsers) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
    this.rollover = rollover;
    this.enabled = enabled;
    this.allowedUsers = new HashSet<>(Arrays.asList(allowedUsers.split(",")));
    this.allowedUsers.removeIf(String::isBlank);
    var trimmed = this.allowedUsers.stream().map(String::trim).toList();
    this.allowedUsers.clear();
    this.allowedUsers.addAll(trimmed);
  }

  private boolean enabledFor(UUID userId) {
    return enabled && (allowedUsers.isEmpty() || allowedUsers.contains(userId.toString()));
  }

  @Transactional(readOnly = true)
  public Availability availability(UUID userId) {
    requireUser(userId, false);
    boolean hasRecords =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                """
select exists(select 1 from purchase_outcomes po join saved_items si on si.id=po.item_id
where si.user_id=? and si.status<>'DROPPED')
""",
                Boolean.class,
                userId));
    return new Availability(enabledFor(userId), hasRecords);
  }

  @Transactional(readOnly = true)
  public Page list(UUID userId, boolean records, int offset, int limit) {
    requireUser(userId, false);
    if (offset < 0 || offset > 100000 || limit < 1 || limit > 100)
      throw error(HttpStatus.BAD_REQUEST, "Invalid pagination.");
    var rows =
        jdbc.query(
            SELECT
                + " where si.user_id=? and si.status<> 'DROPPED' and "
                + (records
                    ? "(po.status in ('PURCHASED','DECLINED') or pd.id is not null)"
                    : "si.status='SAVED' and pd.id is null and (po.status is null or"
                          + " po.status='CONSIDERING')")
                + " order by si.created_at desc, si.id desc limit ? offset ?",
            this::map,
            userId,
            limit + 1,
            offset);
    return new Page(
        rows.stream().limit(limit).toList(), rows.size() > limit ? offset + limit : null);
  }

  @Transactional(readOnly = true)
  public Item get(UUID userId, UUID itemId) {
    requireUser(userId, false);
    return read(userId, itemId);
  }

  private Item read(UUID userId, UUID itemId) {
    var rows =
        jdbc.query(
            SELECT + " where si.user_id=? and si.id=? and si.status<>'DROPPED'",
            this::map,
            userId,
            itemId);
    if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "상품을 찾을 수 없어요.");
    return rows.getFirst();
  }

  @Transactional
  public Item change(UUID userId, UUID itemId, Change request) {
    ZoneId zone = requireUser(userId, true); // serializes new outcome writes for this account
    if (!enabledFor(userId)) throw error(HttpStatus.FORBIDDEN, "새 구매 정리가 아직 활성화되지 않았어요.");
    validate(request, zone);
    var rows =
        jdbc.queryForList(
            "select status from saved_items where user_id=? and id=? for update", userId, itemId);
    if (rows.isEmpty() || "DROPPED".equals(rows.getFirst().get("status")))
      throw error(HttpStatus.NOT_FOUND, "상품을 찾을 수 없어요.");
    String requestJson = encode(request);
    var replay =
        jdbc.queryForList(
            "select request_json = ?::jsonb as same, response_json::text as response from"
                + " purchase_outcome_mutations where item_id=? and mutation_id=?",
            requestJson,
            itemId,
            request.mutationId());
    if (!replay.isEmpty()) {
      if (!Boolean.TRUE.equals(replay.getFirst().get("same")))
        throw error(HttpStatus.CONFLICT, "이미 사용한 요청 식별자예요.");
      try {
        return json.readValue((String) replay.getFirst().get("response"), Item.class);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
    }
    Item current = read(userId, itemId);
    if (current.legacyDecision()) throw error(HttpStatus.CONFLICT, "기존 설문 결정은 기존 소비 기록에서 수정해 주세요.");
    if (current.revision() != request.expectedRevision())
      throw error(HttpStatus.CONFLICT, "다른 화면에서 변경됐어요. 새로고침 후 다시 시도해 주세요.");
    UUID oldCycle =
        jdbc
            .query(
                "select budget_cycle_id from purchase_outcomes where item_id=?",
                (rs, n) -> rs.getObject(1, UUID.class),
                itemId)
            .stream()
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    UUID newCycle = null;
    if (request.status() == Status.PURCHASED) {
      String month = YearMonth.from(request.purchasedOn()).toString();
      rollover.ensureBudgetCycle(userId, month);
      var cycles =
          jdbc.query(
              "select id from budget_cycles where user_id=? and year_month=?",
              (rs, n) -> rs.getObject(1, UUID.class),
              userId,
              month);
      if (cycles.isEmpty()) throw error(HttpStatus.CONFLICT, "해당 월의 예산 기록이 없어요. 구매 날짜를 확인해 주세요.");
      newCycle = cycles.getFirst();
    }
    // deterministic month order also covers backdated corrections across two budget cycles
    Set<UUID> cycles = new TreeSet<>();
    if (oldCycle != null) cycles.add(oldCycle);
    if (newCycle != null) cycles.add(newCycle);
    for (UUID cycle : cycles)
      jdbc.queryForObject("select id from budget_cycles where id=? for update", UUID.class, cycle);
    Map<UUID, Long> deltas = new HashMap<>();
    if (oldCycle != null) deltas.merge(oldCycle, -(long) current.actualPrice(), Long::sum);
    if (newCycle != null) deltas.merge(newCycle, (long) request.actualPrice(), Long::sum);
    for (var delta : deltas.entrySet()) {
      int updated =
          jdbc.update(
              """
update budget_cycles set spent_amount=(spent_amount::bigint+?)::integer, updated_at=now()
where id=? and spent_amount::bigint+? between 0 and 2147483647
""",
              delta.getValue(),
              delta.getKey(),
              delta.getValue());
      if (updated != 1) throw error(HttpStatus.CONFLICT, "예산 합계를 확인해야 해요. 변경을 저장하지 않았어요.");
    }
    jdbc.update(
        """
insert into purchase_outcomes(item_id,status,note,actual_price,purchased_on,budget_cycle_id,revision,updated_at)
values(?,?,?,?,?,?,?,now()) on conflict(item_id) do update set status=excluded.status,note=excluded.note,
  actual_price=excluded.actual_price,purchased_on=excluded.purchased_on,budget_cycle_id=excluded.budget_cycle_id,
  revision=excluded.revision,updated_at=excluded.updated_at
""",
        itemId,
        request.status().name(),
        request.note() == null ? "" : request.note().trim(),
        request.actualPrice(),
        request.purchasedOn(),
        newCycle,
        current.revision() + 1);
    // Existing clients keep their known SAVED/GO/STOP enum; no synthetic survey/decision is
    // written.
    String itemStatus =
        switch (request.status()) {
          case CONSIDERING -> "SAVED";
          case PURCHASED -> "GO";
          case DECLINED -> "STOP";
        };
    if ("SAVED".equals(itemStatus)) {
      boolean duplicate =
          Boolean.TRUE.equals(
              jdbc.queryForObject(
                  """
select exists(select 1 from saved_items a join saved_items b on b.user_id=a.user_id and b.normalized_url=a.normalized_url
where a.id=? and b.id<>a.id and b.status='SAVED')
""",
                  Boolean.class,
                  itemId));
      if (duplicate) throw error(HttpStatus.CONFLICT, "같은 링크의 고민 중인 상품이 이미 있어요.");
    }
    jdbc.update("update saved_items set status=?,updated_at=now() where id=?", itemStatus, itemId);
    Item result = read(userId, itemId);
    jdbc.update(
        "insert into purchase_outcome_mutations(item_id,mutation_id,request_json,response_json)"
            + " values(?,?,?::jsonb,?::jsonb)",
        itemId,
        request.mutationId(),
        requestJson,
        encode(result));
    return result;
  }

  private void validate(Change r, ZoneId zone) {
    if (r == null
        || r.mutationId() == null
        || r.expectedRevision() == null
        || r.expectedRevision() < 0
        || r.status() == null) throw error(HttpStatus.BAD_REQUEST, "요청 식별자·버전·상태가 필요해요.");
    if (r.note() != null && r.note().length() > 500)
      throw error(HttpStatus.BAD_REQUEST, "메모는 500자까지 입력해 주세요.");
    if (r.status() == Status.PURCHASED) {
      if (r.actualPrice() == null
          || r.actualPrice() < 0
          || r.purchasedOn() == null
          || r.purchasedOn().isAfter(LocalDate.now(clock.withZone(zone))))
        throw error(HttpStatus.BAD_REQUEST, "실제 결제 금액과 미래가 아닌 구매 날짜를 입력해 주세요.");
    } else if (r.actualPrice() != null || r.purchasedOn() != null)
      throw error(HttpStatus.BAD_REQUEST, "구매 전 상태에는 결제 정보를 넣을 수 없어요.");
  }

  private ZoneId requireUser(UUID id, boolean lock) {
    var users =
        jdbc.queryForList(
            "select status,onboarding_status from users where id=?"
                + (lock ? " for no key update" : ""),
            id);
    if (users.isEmpty()) throw error(HttpStatus.NOT_FOUND, "계정을 찾을 수 없어요.");
    var u = users.getFirst();
    if (!"ACTIVE".equals(u.get("status")) || !"COMPLETED".equals(u.get("onboarding_status")))
      throw error(HttpStatus.FORBIDDEN, "온보딩을 완료한 계정만 이용할 수 있어요.");
    var zones =
        jdbc.query(
            "select timezone from user_profiles where user_id=?", (rs, n) -> rs.getString(1), id);
    try {
      return ZoneId.of(
          zones.isEmpty() || zones.getFirst() == null ? "Asia/Seoul" : zones.getFirst());
    } catch (DateTimeException e) {
      return ZoneId.of("Asia/Seoul");
    }
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private Item map(ResultSet rs, int row) throws SQLException {
    return new Item(
        rs.getObject("id", UUID.class),
        rs.getString("title"),
        rs.getString("image_url"),
        rs.getString("original_url"),
        rs.getObject("listed_price", Integer.class),
        rs.getString("currency_code"),
        rs.getString("category"),
        rs.getString("outcome_status"),
        rs.getString("note"),
        rs.getObject("actual_price", Integer.class),
        rs.getObject("purchased_on", LocalDate.class),
        rs.getLong("revision"),
        rs.getBoolean("legacy_decision"));
  }

  private static ResponseStatusException error(HttpStatus status, String message) {
    return new ResponseStatusException(status, message);
  }
}
