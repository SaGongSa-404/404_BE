const extractButton = document.querySelector("#extractButton");
const highlightButton = document.querySelector("#highlightButton");
const captureButton = document.querySelector("#captureButton");
const copyButton = document.querySelector("#copyButton");
const statusText = document.querySelector("#statusText");
const resultCard = document.querySelector("#resultCard");
const previewImage = document.querySelector("#previewImage");
const titleValue = document.querySelector("#titleValue");
const priceValue = document.querySelector("#priceValue");
const imageValue = document.querySelector("#imageValue");
const urlValue = document.querySelector("#urlValue");
const evidenceValue = document.querySelector("#evidenceValue");

let lastResult = null;

async function getActiveTab() {
  const [tab] = await chrome.tabs.query({
    active: true,
    currentWindow: true
  });
  return tab;
}

function isSupportedUrl(url) {
  return /^https:\/\/(www|m)\.coupang\.com\//.test(url || "");
}

async function sendToActiveTab(message) {
  const tab = await getActiveTab();
  if (!tab?.id) {
    throw new Error("활성 탭을 찾을 수 없습니다.");
  }

  if (!isSupportedUrl(tab.url)) {
    throw new Error("쿠팡 상품 페이지에서 실행해 주세요.");
  }

  return chrome.tabs.sendMessage(tab.id, message);
}

function renderResult(result) {
  const evidence = [];
  if (result.evidence?.dom?.price) evidence.push("DOM 가격");
  if (result.evidence?.jsonLd?.price) evidence.push("JSON-LD 가격");
  if (result.evidence?.bodyPrice?.value) evidence.push("본문 가격");
  if (result.evidence?.dom?.image || result.evidence?.jsonLd?.image) evidence.push("메타/이미지");

  titleValue.textContent = result.title || "-";
  priceValue.textContent = result.price ? `${result.price}원` : "-";
  imageValue.textContent = result.image || "-";
  urlValue.textContent = result.url || "-";
  evidenceValue.textContent = evidence.join(", ") || "탐지 근거 없음";

  if (result.image) {
    previewImage.src = result.image;
    previewImage.style.display = "block";
  } else {
    previewImage.removeAttribute("src");
    previewImage.style.display = "none";
  }

  resultCard.classList.remove("hidden");
  copyButton.disabled = false;
  lastResult = result;
}

extractButton.addEventListener("click", async () => {
  statusText.textContent = "상품 정보 추출 중...";
  try {
    const response = await sendToActiveTab({ type: "COUPANG_EXTRACT" });
    if (!response?.ok) {
      throw new Error(response?.error || "추출에 실패했습니다.");
    }

    renderResult(response.result);
    statusText.textContent = response.result.success
      ? "추출이 끝났습니다."
      : "일부 정보만 찾았습니다.";
  } catch (error) {
    statusText.textContent = error instanceof Error ? error.message : String(error);
  }
});

highlightButton.addEventListener("click", async () => {
  statusText.textContent = "가격 위치를 찾는 중...";
  try {
    const response = await sendToActiveTab({ type: "COUPANG_HIGHLIGHT_PRICE" });
    if (!response?.ok) {
      throw new Error(response?.error || "가격 노드를 찾지 못했습니다.");
    }

    statusText.textContent = `가격 후보를 강조했습니다: ${response.selector}`;
  } catch (error) {
    statusText.textContent = error instanceof Error ? error.message : String(error);
  }
});

captureButton.addEventListener("click", async () => {
  statusText.textContent = "현재 보이는 영역을 캡처 중...";
  try {
    const tab = await getActiveTab();
    if (!isSupportedUrl(tab?.url)) {
      throw new Error("쿠팡 탭에서 실행해 주세요.");
    }

    const response = await chrome.runtime.sendMessage({
      type: "CAPTURE_VISIBLE_TAB"
    });
    if (!response?.ok) {
      throw new Error(response?.error || "캡처 저장에 실패했습니다.");
    }

    statusText.textContent = `캡처를 저장했습니다: ${response.filename}`;
  } catch (error) {
    statusText.textContent = error instanceof Error ? error.message : String(error);
  }
});

copyButton.addEventListener("click", async () => {
  if (!lastResult) return;
  await navigator.clipboard.writeText(JSON.stringify(lastResult, null, 2));
  statusText.textContent = "JSON을 클립보드에 복사했습니다.";
});
