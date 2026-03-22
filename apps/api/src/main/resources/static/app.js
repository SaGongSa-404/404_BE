const state = {
  lastSavedPayload: null,
  enhancementPollTimer: null,
};

const elements = {
  userId: document.getElementById("userId"),
  targetDate: document.getElementById("targetDate"),
  contentUrl: document.getElementById("contentUrl"),
  saveButton: document.getElementById("saveButton"),
  refreshAllButton: document.getElementById("refreshAllButton"),
  useDemoButton: document.getElementById("useDemoButton"),
  statusBox: document.getElementById("statusBox"),
  lastSavedCard: document.getElementById("lastSavedCard"),
  openCardCount: document.getElementById("openCardCount"),
  completedTodayCount: document.getElementById("completedTodayCount"),
  recommendedCard: document.getElementById("recommendedCard"),
  cardList: document.getElementById("cardList"),
  savedCount: document.getElementById("savedCount"),
  completedCount: document.getElementById("completedCount"),
  completionRate: document.getElementById("completionRate"),
  pendingCount: document.getElementById("pendingCount"),
  insightMessage: document.getElementById("insightMessage"),
  savedCategories: document.getElementById("savedCategories"),
  completedCategories: document.getElementById("completedCategories"),
  recentCompletions: document.getElementById("recentCompletions"),
  detailModal: document.getElementById("detailModal"),
  detailContent: document.getElementById("detailContent"),
  detailCloseButton: document.getElementById("detailCloseButton"),
  pendingEnhancementCount: document.getElementById("pendingEnhancementCount"),
  enhancedCount: document.getElementById("enhancedCount"),
  skippedCount: document.getElementById("skippedCount"),
  failedCount: document.getElementById("failedCount"),
  recentAiEvents: document.getElementById("recentAiEvents"),
};

const STORAGE_KEYS = {
  userId: "action-deck-lab.user-id",
  targetDate: "action-deck-lab.target-date",
};

initialize();

function initialize() {
  elements.userId.value =
    localStorage.getItem(STORAGE_KEYS.userId) ||
    "11111111-1111-1111-1111-111111111111";
  elements.targetDate.value =
    localStorage.getItem(STORAGE_KEYS.targetDate) ||
    new Date().toISOString().slice(0, 10);

  bindEvents();
  refreshAll();
}

function bindEvents() {
  elements.saveButton.addEventListener("click", handleSave);
  elements.refreshAllButton.addEventListener("click", refreshAll);
  elements.detailCloseButton.addEventListener("click", closeDetailModal);
  document.querySelectorAll("[data-close-detail]").forEach((node) => {
    node.addEventListener("click", closeDetailModal);
  });
  elements.useDemoButton.addEventListener("click", () => {
    elements.userId.value = "11111111-1111-1111-1111-111111111111";
    persistSession();
    setStatus("데모 사용자 ID를 채웠다.", "success");
    refreshAll();
  });

  elements.userId.addEventListener("change", () => {
    persistSession();
    refreshAll();
  });

  elements.targetDate.addEventListener("change", () => {
    persistSession();
    refreshAll();
  });
}

function persistSession() {
  localStorage.setItem(STORAGE_KEYS.userId, elements.userId.value.trim());
  localStorage.setItem(STORAGE_KEYS.targetDate, elements.targetDate.value);
}

async function handleSave() {
  persistSession();

  const payload = {
    url: elements.contentUrl.value.trim(),
  };

  if (!payload.url) {
    setStatus("링크를 먼저 입력해줘.", "error");
    return;
  }

  setStatus("링크를 저장하고 AI 업그레이드를 준비 중이에요...", "");

  try {
    const response = await apiRequest("/api/v1/content-links", {
      method: "POST",
      body: JSON.stringify(payload),
    });

    state.lastSavedPayload = response;
    renderLastSavedCard(response);
    clearComposer();
    setStatus("카드를 만들었고 AI 업그레이드를 확인하는 중이에요.", "success");
    await refreshAll();
    startEnhancementPolling(response.practiceCard.id);
  } catch (error) {
    setStatus(humanizeError(error), "error");
  }
}

async function refreshAll() {
  persistSession();
  try {
    const [home, weeklyReport, monitoring] = await Promise.all([
      apiRequest(`/api/v1/facades/home?date=${encodeURIComponent(elements.targetDate.value)}`),
      apiRequest(`/api/v1/facades/reports/weekly?date=${encodeURIComponent(elements.targetDate.value)}`),
      apiRequest("/api/v1/monitoring/ai-enhancements"),
    ]);

    renderHome(home);
    renderWeeklyReport(weeklyReport);
    renderMonitoring(monitoring);
    if (!state.lastSavedPayload) {
      setStatus("덱과 리포트, AI 모니터링을 불러왔다.", "");
    }
  } catch (error) {
    setStatus(humanizeError(error), "error");
  }
}

async function completeCard(cardId) {
  const completionNote = window.prompt("간단한 완료 메모를 남길래?", "실제로 해봤다");
  try {
    await apiRequest(`/api/v1/practice-cards/${cardId}/complete`, {
      method: "POST",
      body: JSON.stringify({
        completionNote: completionNote && completionNote.trim() ? completionNote.trim() : null,
      }),
    });
    setStatus("카드를 완료 처리했다.", "success");
    await refreshAll();
  } catch (error) {
    setStatus(humanizeError(error), "error");
  }
}

async function regenerateCard(cardId) {
  try {
    await apiRequest(`/api/v1/practice-cards/${cardId}/regenerate`, {
      method: "POST",
    });
    setStatus("AI 재생성을 요청했어요. 잠시 뒤 자동으로 갱신할게요.", "success");
    await refreshAll();
    startEnhancementPolling(cardId);
  } catch (error) {
    setStatus(humanizeError(error), "error");
  }
}

async function reportCardIssue(cardId) {
  const reason = window.prompt("어떤 점이 어색했는지 적어줄래?", "카테고리나 행동이 어색해요");
  if (reason === null) {
    return;
  }

  try {
    await apiRequest(`/api/v1/practice-cards/${cardId}/report-issue`, {
      method: "POST",
      body: JSON.stringify({
        reason: reason.trim() || "카드 이슈 신고",
      }),
    });
    setStatus("카드 이슈를 기록했어요. 필요하면 AI 재생성을 다시 눌러주세요.", "success");
    await refreshAll();
  } catch (error) {
    setStatus(humanizeError(error), "error");
  }
}

async function openCardDetail(cardId) {
  try {
    const card = await apiRequest(`/api/v1/practice-cards/${cardId}`);
    renderCardDetail(card);
    elements.detailModal.classList.remove("hidden");
    elements.detailModal.setAttribute("aria-hidden", "false");
  } catch (error) {
    setStatus(humanizeError(error), "error");
  }
}

function closeDetailModal() {
  elements.detailModal.classList.add("hidden");
  elements.detailModal.setAttribute("aria-hidden", "true");
}

async function apiRequest(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-User-Id": elements.userId.value.trim(),
      ...(options.headers || {}),
    },
  });

  if (!response.ok) {
    let message = "요청 처리에 실패했다.";
    let code = "REQUEST_FAILED";
    try {
      const errorPayload = await response.json();
      message = errorPayload.message || message;
      code = errorPayload.code || code;
    } catch (ignored) {
    }
    const error = new Error(message);
    error.code = code;
    throw error;
  }

  return response.json();
}

function clearComposer() {
  elements.contentUrl.value = "";
}

function setStatus(message, type) {
  elements.statusBox.textContent = message;
  elements.statusBox.className = "status-box";
  if (type === "success") {
    elements.statusBox.classList.add("is-success");
  }
  if (type === "error") {
    elements.statusBox.classList.add("is-error");
  }
}

function renderLastSavedCard(payload) {
  if (!payload || !payload.practiceCard) {
    elements.lastSavedCard.innerHTML = "아직 생성된 카드가 없다.";
    return;
  }

  const card = payload.practiceCard;
  const saved = payload.savedContent;
  elements.lastSavedCard.className = "saved-card-preview";
  elements.lastSavedCard.innerHTML = `
    <div class="saved-card-meta">
      <span class="pill">${escapeHtml(card.categoryLabel)}</span>
      <span>${escapeHtml(saved.sourceDomain)}</span>
    </div>
    <div class="card-status-stack">${renderEnhancementStatus(card)}</div>
    <h3 class="saved-card-title">${escapeHtml(card.actionTitle)}</h3>
    <p class="saved-card-detail">${escapeHtml(card.actionDetail)}</p>
    <p class="saved-card-detail"><strong>원본 제목:</strong> ${escapeHtml(saved.title)}</p>
    <div class="action-row">
      <button class="button ghost" data-regenerate-card="${card.id}">AI 재생성</button>
      <button class="button ghost" data-report-card="${card.id}">카드 신고</button>
      <button class="button ghost" data-detail-card="${card.id}">자세히 보기</button>
    </div>
  `;
  elements.lastSavedCard.querySelector("[data-detail-card]")?.addEventListener("click", () => openCardDetail(card.id));
  elements.lastSavedCard.querySelector("[data-regenerate-card]")?.addEventListener("click", () => regenerateCard(card.id));
  elements.lastSavedCard.querySelector("[data-report-card]")?.addEventListener("click", () => reportCardIssue(card.id));
}

function renderHome(home) {
  elements.openCardCount.textContent = home.openCardCount ?? 0;
  elements.completedTodayCount.textContent = home.completedTodayCount ?? 0;

  if (home.recommendedCard) {
    elements.recommendedCard.className = "recommended-card";
    elements.recommendedCard.innerHTML = `
      <span class="pill">추천 카드</span>
      <div class="card-status-stack">${renderEnhancementStatus(home.recommendedCard)}</div>
      <h3>${escapeHtml(home.recommendedCard.actionTitle)}</h3>
      <p>${escapeHtml(home.recommendedCard.actionDetail)}</p>
    `;
  } else {
    elements.recommendedCard.className = "recommended-card empty-state";
    elements.recommendedCard.textContent = "추천 카드가 아직 없다.";
  }

  if (!home.cards || home.cards.length === 0) {
    elements.cardList.innerHTML = `<div class="empty-state">오늘 보여줄 카드가 없다.</div>`;
    return;
  }

  elements.cardList.innerHTML = home.cards
    .map(
      (card) => `
        <article class="card-item">
          <div class="card-topline">
            <span class="pill">${escapeHtml(card.categoryLabel)}</span>
            <div class="card-status-stack">
              <span class="card-meta">${escapeHtml(card.status)}</span>
              ${renderEnhancementStatus(card)}
            </div>
          </div>
          <h3 class="card-title">${escapeHtml(card.actionTitle)}</h3>
          <p class="card-detail">${escapeHtml(card.actionDetail)}</p>
          <p class="card-detail"><strong>원본:</strong> ${escapeHtml(card.sourceTitle)}</p>
          <div class="card-footer">
            <div class="card-meta">
              <span>${escapeHtml(card.sourceDomain)}</span>
              <span>${card.estimatedMinutes}분</span>
              <span>${escapeHtml(card.energyLevel)}</span>
            </div>
            <div class="action-row">
              <button class="button ghost" data-detail-card="${card.id}">자세히 보기</button>
              <button class="button ghost" data-regenerate-card="${card.id}">AI 재생성</button>
              <button class="button ghost" data-report-card="${card.id}">카드 신고</button>
              ${
                card.status === "OPEN"
                  ? `<button class="button primary" data-complete-card="${card.id}">실천 완료</button>`
                  : `<span class="card-meta">완료 메모: ${escapeHtml(card.completionNote || "-")}</span>`
              }
            </div>
          </div>
        </article>
      `
    )
    .join("");

  elements.cardList.querySelectorAll("[data-complete-card]").forEach((button) => {
    button.addEventListener("click", () => completeCard(button.dataset.completeCard));
  });
  elements.cardList.querySelectorAll("[data-detail-card]").forEach((button) => {
    button.addEventListener("click", () => openCardDetail(button.dataset.detailCard));
  });
  elements.cardList.querySelectorAll("[data-regenerate-card]").forEach((button) => {
    button.addEventListener("click", () => regenerateCard(button.dataset.regenerateCard));
  });
  elements.cardList.querySelectorAll("[data-report-card]").forEach((button) => {
    button.addEventListener("click", () => reportCardIssue(button.dataset.reportCard));
  });
}

function renderWeeklyReport(report) {
  elements.savedCount.textContent = report.savedCount ?? 0;
  elements.completedCount.textContent = report.completedCount ?? 0;
  elements.completionRate.textContent = `${report.completionRate ?? 0}%`;
  elements.pendingCount.textContent = report.pendingCount ?? 0;
  elements.insightMessage.textContent = report.insightMessage || "아직 리포트 데이터가 없다.";

  renderMiniList(
    elements.savedCategories,
    report.savedCategories,
    (item) => `
      <span>${escapeHtml(item.categoryLabel)}</span>
      <strong>${item.count}</strong>
    `
  );

  renderMiniList(
    elements.completedCategories,
    report.completedCategories,
    (item) => `
      <span>${escapeHtml(item.categoryLabel)}</span>
      <strong>${item.count}</strong>
    `
  );

  renderMiniList(
    elements.recentCompletions,
    report.recentCompletions,
    (item) => `
      <span>${escapeHtml(item.actionTitle)}</span>
      <strong>${escapeHtml(item.categoryLabel)}</strong>
    `
  );
}

function renderMonitoring(monitoring) {
  elements.pendingEnhancementCount.textContent = monitoring.pendingCount ?? 0;
  elements.enhancedCount.textContent = monitoring.enhancedCount ?? 0;
  elements.skippedCount.textContent = monitoring.skippedCount ?? 0;
  elements.failedCount.textContent = monitoring.failedCount ?? 0;

  renderMiniList(
    elements.recentAiEvents,
    monitoring.recentEvents,
    (item) => `
      <span>${escapeHtml(item.eventTypeLabel)}</span>
      <strong>${escapeHtml(item.note || "-")}</strong>
    `
  );
}

function renderMiniList(container, items, renderer) {
  if (!items || items.length === 0) {
    container.innerHTML = `<li class="empty-item">아직 없음</li>`;
    return;
  }

  container.innerHTML = items.map((item) => `<li>${renderer(item)}</li>`).join("");
}

function renderCardDetail(card) {
  const ideas = Array.isArray(card.ideaOptions) ? card.ideaOptions : [];
  const detailSections = Array.isArray(card.detailSections) ? card.detailSections : [];
  elements.detailContent.innerHTML = `
    <div class="saved-card-meta">
      <span class="pill">${escapeHtml(card.categoryLabel)}</span>
      <span>${escapeHtml(card.sourceDomain)}</span>
    </div>
    <div class="card-status-stack">${renderEnhancementStatus(card)}</div>
    <h2>${escapeHtml(card.actionTitle)}</h2>
    <p class="saved-card-detail">${escapeHtml(card.actionDetail)}</p>
    <div class="detail-section">
      <h3>${escapeHtml(card.detailTitle || "카드 상세")}</h3>
      <p>${escapeHtml(card.detailBody || "추가 설명이 아직 없다.")}</p>
    </div>
    ${
      detailSections.length > 0
        ? detailSections
            .map(
              (section) => `
                <div class="detail-section">
                  <h3>${escapeHtml(section.title || "상세 섹션")}</h3>
                  ${section.body ? `<p>${escapeHtml(section.body)}</p>` : ""}
                  ${
                    Array.isArray(section.items) && section.items.length > 0
                      ? `<ul class="idea-list">${section.items.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>`
                      : ""
                  }
                </div>
              `
            )
            .join("")
        : ""
    }
    <div class="detail-section">
      <h3>함께 해볼 선택지</h3>
      ${
        ideas.length > 0
          ? `<ul class="idea-list">${ideas.map((idea) => `<li>${escapeHtml(idea)}</li>`).join("")}</ul>`
          : `<p>지금은 함께 보여줄 선택지가 없다.</p>`
      }
    </div>
    <div class="detail-section">
      <h3>원본 콘텐츠</h3>
      <p>${escapeHtml(card.sourceTitle)}</p>
      <p><a href="${escapeHtml(card.sourceUrl)}" target="_blank" rel="noreferrer">${escapeHtml(card.sourceUrl)}</a></p>
    </div>
  `;
}

function renderEnhancementStatus(card) {
  if (!card.enhancementStatusLabel) {
    return "";
  }
  const cssClass = mapEnhancementCss(card.enhancementStatus);
  const note = card.enhancementNote ? `<span class="card-meta">${escapeHtml(card.enhancementNote)}</span>` : "";
  return `<span class="status-pill ${cssClass}">${escapeHtml(card.enhancementStatusLabel)}</span>${note}`;
}

function mapEnhancementCss(status) {
  switch (status) {
    case "PENDING":
      return "pending";
    case "ENHANCED":
      return "enhanced";
    case "FAILED":
      return "failed";
    case "SKIPPED":
      return "skipped";
    case "REPORTED":
      return "reported";
    default:
      return "";
  }
}

function startEnhancementPolling(cardId) {
  if (state.enhancementPollTimer) {
    clearInterval(state.enhancementPollTimer);
  }
  let attempts = 0;
  state.enhancementPollTimer = setInterval(async () => {
    attempts += 1;
    try {
      const card = await apiRequest(`/api/v1/practice-cards/${cardId}`);
      if (card.enhancementStatus !== "PENDING" || attempts >= 15) {
        clearInterval(state.enhancementPollTimer);
        state.enhancementPollTimer = null;
        if (card.enhancementStatus === "ENHANCED") {
          setStatus("AI 업그레이드가 반영됐어요.", "success");
        } else if (card.enhancementStatus === "FAILED") {
          setStatus("AI 업그레이드가 실패해서 기본 카드를 유지했어요.", "error");
        } else if (card.enhancementStatus === "SKIPPED") {
          setStatus("기본 카드가 더 적절해서 AI 업그레이드를 건너뛰었어요.", "");
        }
        await refreshAll();
      }
    } catch (error) {
      clearInterval(state.enhancementPollTimer);
      state.enhancementPollTimer = null;
    }
  }, 2000);
}

function humanizeError(error) {
  if (error?.code === "UNSUPPORTED_LINK") {
    return "지금 링크는 프로필/목록형이라 처리하기 어려워요. 개별 글, 영상, 포스트 링크를 넣어주세요.";
  }
  return error?.message || "요청 처리에 실패했다.";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
