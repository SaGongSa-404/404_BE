package com.sagongsa.backend.purchase;

import static com.sagongsa.backend.purchase.PurchaseModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.budget.BudgetCycleRolloverService;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = "app.purchase-comparison.enabled=true")
@AutoConfigureMockMvc
class PurchaseApiIntegrationTest extends PostgreSqlContainerTest {
  @Autowired PurchaseService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired Clock clock;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired BudgetCycleRolloverService rollover;
  UUID user, item;
  LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

  @BeforeEach
  void setup() {
    jdbc.execute("truncate table users cascade");
    user = UUID.randomUUID();
    item = UUID.randomUUID();
    jdbc.update(
        "insert into users(id,status,onboarding_status,created_at,updated_at)"
            + " values(?,'ACTIVE','COMPLETED',now(),now())",
        user);
    jdbc.update(
        "insert into user_profiles(user_id,nickname,mascot_name,timezone,created_at,updated_at)"
            + " values(?,'tester','너굴','Asia/Seoul',now(),now())",
        user);
    budget(YearMonth.from(today).toString(), 1000);
    addItem(item);
  }

  void budget(String month, int spent) {
    jdbc.update(
        """
insert into budget_cycles(id,user_id,year_month,monthly_budget_amount,spent_amount,warning_threshold_rate,created_at,updated_at)
values(?,?,?,500000,?,80,now(),now())
""",
        UUID.randomUUID(),
        user,
        month,
        spent);
  }

  void addItem(UUID id) {
    jdbc.update(
        """
insert into saved_items(id,user_id,input_source,title,listed_price,currency_code,category,status,created_at,updated_at)
values(?,?,'DIRECT_INPUT','후보',10000,'KRW','FASHION','SAVED',now(),now())
""",
        id,
        user);
  }

  Change change(long version, Status status, Integer price, LocalDate date) {
    return new Change(UUID.randomUUID(), version, status, "고민 메모", price, date);
  }

  int spent(String month) {
    return jdbc.queryForObject(
        "select spent_amount from budget_cycles where user_id=? and year_month=?",
        Integer.class,
        user,
        month);
  }

  @Test
  void accountWithdrawalRemovesNewRecordsAndRetryPayloads() throws Exception {
    service.change(user, item, change(0, Status.PURCHASED, 9000, today));
    mvc.perform(delete("/api/v1/users/me").header("X-User-Id", user))
        .andExpect(status().isNoContent());
    assertThat(jdbc.queryForObject("select count(*) from purchase_outcomes", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("select count(*) from purchase_outcome_mutations", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from budget_cycles where user_id=?", Integer.class, user))
        .isZero();
  }

  @Test
  void purchaseCorrectionAndReturnToConsideringNeverInventSurvey() {
    var first = change(0, Status.PURCHASED, 9000, today);
    assertThat(service.change(user, item, first).revision()).isEqualTo(1);
    assertThat(service.change(user, item, first).revision()).isEqualTo(1);
    assertThat(spent(YearMonth.from(today).toString())).isEqualTo(10000);
    service.change(user, item, change(1, Status.PURCHASED, 7000, today));
    assertThat(spent(YearMonth.from(today).toString())).isEqualTo(8000);
    service.change(user, item, change(2, Status.CONSIDERING, null, null));
    assertThat(spent(YearMonth.from(today).toString())).isEqualTo(1000);
    assertThat(jdbc.queryForObject("select count(*) from purchase_decisions", Integer.class))
        .isZero();
    assertThat(service.list(user, false, 0, 50).items()).hasSize(1);
  }

  @Test
  void crossMonthCorrectionAndDeclineReverseOnlyOurContribution() {
    var lastMonth = today.minusMonths(1);
    budget(YearMonth.from(lastMonth).toString(), 500);
    service.change(user, item, change(0, Status.PURCHASED, 9000, today));
    service.change(user, item, change(1, Status.PURCHASED, 7000, lastMonth));
    assertThat(spent(YearMonth.from(today).toString())).isEqualTo(1000);
    assertThat(spent(YearMonth.from(lastMonth).toString())).isEqualTo(7500);
    service.change(user, item, change(2, Status.DECLINED, null, null));
    assertThat(spent(YearMonth.from(lastMonth).toString())).isEqualTo(500);
  }

  @Test
  void concurrentRetryCountsOnceAndStaleDifferentRequestIsRejected() throws Exception {
    var request = change(0, Status.PURCHASED, 9000, today);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Item> task =
          () -> {
            start.await();
            return service.change(user, item, request);
          };
      var a = executor.submit(task);
      var b = executor.submit(task);
      start.countDown();
      assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
    }
    assertThat(spent(YearMonth.from(today).toString())).isEqualTo(10000);
    assertThatThrownBy(() -> service.change(user, item, change(0, Status.DECLINED, null, null)))
        .isInstanceOf(ResponseStatusException.class);
    var bad = new Change(request.mutationId(), 0L, Status.PURCHASED, "changed", 8000, today);
    assertThatThrownBy(() -> service.change(user, item, bad))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void mutationFailureRollsBackBudgetAndItem() {
    jdbc.execute(
        "alter table purchase_outcome_mutations add constraint reject_test check (item_id <> '"
            + item
            + "'::uuid)");
    try {
      assertThatThrownBy(() -> service.change(user, item, change(0, Status.PURCHASED, 9000, today)))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(spent(YearMonth.from(today).toString())).isEqualTo(1000);
      assertThat(service.get(user, item).revision()).isZero();
      assertThat(
              jdbc.queryForObject("select status from saved_items where id=?", String.class, item))
          .isEqualTo("SAVED");
    } finally {
      jdbc.execute("alter table purchase_outcome_mutations drop constraint reject_test");
    }
  }

  @Test
  void apiOwnershipValidationAndRecordsPagination() throws Exception {
    mvc.perform(get("/api/v1/purchase-items/" + UUID.randomUUID()).header("X-User-Id", user))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/purchase-items?offset=-1").header("X-User-Id", user))
        .andExpect(status().isBadRequest());
    var other = UUID.randomUUID();
    jdbc.update(
        "insert into users(id,status,onboarding_status,created_at,updated_at)"
            + " values(?,'ACTIVE','COMPLETED',now(),now())",
        other);
    mvc.perform(
            put("/api/v1/purchase-items/" + item)
                .header("X-User-Id", other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(change(0, Status.DECLINED, null, null))))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/v1/purchase-items/" + item)
                .header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(change(0, Status.PURCHASED, null, today))))
        .andExpect(status().isBadRequest());
    service.change(user, item, change(0, Status.DECLINED, null, null));
    mvc.perform(get("/api/v1/purchase-items?records=true").header("X-User-Id", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].status").value("DECLINED"));
    mvc.perform(get("/api/v1/wishlist/items").header("X-User-Id", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    assertThat(service.list(user, false, 0, 50).items()).isEmpty();
  }

  @Test
  void legacyDecisionCannotDoubleBookNewOutcomeAndExistingContractRemains() throws Exception {
    service.change(user, item, change(0, Status.CONSIDERING, null, null));
    String payload =
        """
{"itemId":"%s","result":"GO","selfCheckAnswers":[
{"questionCode":"NEED","answerBoolean":false},{"questionCode":"BUDGET","answerBoolean":false},
{"questionCode":"ALTERNATIVE","answerBoolean":false},{"questionCode":"DELAY","answerBoolean":false}]}
"""
            .formatted(item);
    mvc.perform(
            post("/api/v1/decisions")
                .header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isConflict());
    UUID legacy = UUID.randomUUID();
    addItem(legacy);
    jdbc.update(
        "insert into mascot_profiles(user_id,mascot_state,last_state_changed_at,updated_at)"
            + " values(?,'DEFAULT',now(),now())",
        user);
    mvc.perform(
            post("/api/v1/decisions")
                .header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.replace(item.toString(), legacy.toString())))
        .andExpect(status().isCreated());
    assertThat(service.get(user, legacy).legacyDecision()).isTrue();
    int before = spent(YearMonth.from(today).toString());
    assertThatThrownBy(
            () -> service.change(user, legacy, change(0, Status.PURCHASED, 10000, today)))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(spent(YearMonth.from(today).toString())).isEqualTo(before);
  }

  @Test
  void inFlightLegacyDecisionAndNewWriterDoNotDeadlockOnUserForeignKey() throws Exception {
    jdbc.update(
        "insert into mascot_profiles(user_id,mascot_state,last_state_changed_at,updated_at)"
            + " values(?,'DEFAULT',now(),now())",
        user);
    var itemLocked = new CountDownLatch(1);
    var finishLegacy = new CountDownLatch(1);
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    String payload =
        """
{"itemId":"%s","result":"GO","selfCheckAnswers":[
{"questionCode":"NEED","answerBoolean":false},{"questionCode":"BUDGET","answerBoolean":false},
{"questionCode":"ALTERNATIVE","answerBoolean":false},{"questionCode":"DELAY","answerBoolean":false}]}
"""
            .formatted(item);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var legacy =
          executor.submit(
              () ->
                  tx.execute(
                      ignored -> {
                        jdbc.queryForObject(
                            "select id from saved_items where id=? for update", UUID.class, item);
                        itemLocked.countDown();
                        try {
                          if (!finishLegacy.await(10, TimeUnit.SECONDS))
                            throw new AssertionError("legacy release timeout");
                          return mvc.perform(
                                  post("/api/v1/decisions")
                                      .header("X-User-Id", user)
                                      .contentType(MediaType.APPLICATION_JSON)
                                      .content(payload))
                              .andReturn()
                              .getResponse()
                              .getStatus();
                        } catch (Exception e) {
                          throw new RuntimeException(e);
                        }
                      }));
      assertThat(itemLocked.await(5, TimeUnit.SECONDS)).isTrue();
      var modern =
          executor.submit(
              () -> {
                try {
                  service.change(user, item, change(0, Status.PURCHASED, 9000, today));
                  return 200;
                } catch (ResponseStatusException e) {
                  return e.getStatusCode().value();
                }
              });
      boolean waiting = false;
      try {
        for (int i = 0; i < 100 && !waiting; i++) {
          waiting =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      """
                      select exists(select 1 from pg_stat_activity where pid<>pg_backend_pid()
                      and wait_event_type='Lock' and query like 'select status from saved_items%')
                      """,
                      Boolean.class));
          if (!waiting) Thread.sleep(25);
        }
      } finally {
        finishLegacy.countDown();
      }
      assertThat(waiting).isTrue();
      assertThat(legacy.get(10, TimeUnit.SECONDS)).isEqualTo(201);
      assertThat(modern.get(10, TimeUnit.SECONDS)).isEqualTo(409);
    }
    assertThat(spent(YearMonth.from(today).toString())).isEqualTo(11000);
    assertThat(jdbc.queryForObject("select count(*) from purchase_outcomes", Integer.class))
        .isZero();
  }

  @Test
  void monthlyStatsIncludeActualPurchasesWithoutRationalityScore() throws Exception {
    service.change(user, item, change(0, Status.PURCHASED, 9000, today));
    mvc.perform(
            get("/api/v1/users/me/stats")
                .param("yearMonth", YearMonth.from(today).toString())
                .header("X-User-Id", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.spentAmount").value(9000))
        .andExpect(jsonPath("$.boughtCount").value(1))
        .andExpect(jsonPath("$.rationalChoiceRate").isEmpty())
        .andExpect(jsonPath("$.categorySpendAmounts[0].amount").value(9000));
  }

  @Test
  void duplicateSavedUrlRestoreDoesNotLosePurchasedAmount() throws Exception {
    jdbc.update(
        "update saved_items set normalized_url='https://example.com/item' where id=?", item);
    service.change(user, item, change(0, Status.PURCHASED, 9000, today));
    UUID other = UUID.randomUUID();
    addItem(other);
    jdbc.update(
        "update saved_items set normalized_url='https://example.com/item' where id=?", other);
    mvc.perform(
            put("/api/v1/purchase-items/" + item)
                .header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(change(1, Status.CONSIDERING, null, null))))
        .andExpect(status().isConflict());
    assertThat(spent(YearMonth.from(today).toString())).isEqualTo(10000);
    assertThat(service.get(user, item).status()).isEqualTo("PURCHASED");
  }

  @Test
  void gateCannotBeBypassedButReadsRemainAvailable() {
    var off = new PurchaseService(jdbc, json, clock, rollover, false, "");
    assertThat(off.availability(user).enabled()).isFalse();
    assertThat(off.get(user, item).id()).isEqualTo(item);
    assertThatThrownBy(() -> off.change(user, item, change(0, Status.PURCHASED, 9000, today)))
        .isInstanceOf(ResponseStatusException.class);
    var restricted =
        new PurchaseService(jdbc, json, clock, rollover, true, UUID.randomUUID().toString());
    assertThat(restricted.availability(user).enabled()).isFalse();
  }
}
