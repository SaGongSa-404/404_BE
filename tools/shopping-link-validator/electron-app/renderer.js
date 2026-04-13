const urlInput = document.querySelector("#urlInput");
const extractButton = document.querySelector("#extractButton");
const statusText = document.querySelector("#statusText");
const resultPanel = document.querySelector("#resultPanel");
const heroImage = document.querySelector("#heroImage");
const statusValue = document.querySelector("#statusValue");
const finalUrlValue = document.querySelector("#finalUrlValue");
const titleValue = document.querySelector("#titleValue");
const priceValue = document.querySelector("#priceValue");
const imageValue = document.querySelector("#imageValue");
const screenshotValue = document.querySelector("#screenshotValue");
const jsonOutput = document.querySelector("#jsonOutput");

function renderResult(result) {
  resultPanel.classList.remove("hidden");
  statusValue.textContent = result.httpStatus ?? "-";
  finalUrlValue.textContent = result.finalUrl ?? "-";
  titleValue.textContent = result.extraction?.title ?? result.title ?? "-";
  priceValue.textContent = result.extraction?.price ? `${result.extraction.price}원` : "-";
  imageValue.textContent = result.extraction?.image ?? "-";
  screenshotValue.textContent = result.screenshotPath ?? "-";

  if (result.extraction?.image) {
    heroImage.src = result.extraction.image;
    heroImage.style.display = "block";
  } else {
    heroImage.removeAttribute("src");
    heroImage.style.display = "none";
  }

  jsonOutput.textContent = JSON.stringify(result, null, 2);
}

extractButton.addEventListener("click", async () => {
  const url = urlInput.value.trim();
  if (!url) {
    statusText.textContent = "쿠팡 링크를 입력해 주세요.";
    return;
  }

  extractButton.disabled = true;
  statusText.textContent = "숨겨진 브라우저에서 페이지를 열고 있습니다...";

  try {
    const result = await window.coupangApp.extract(url);
    renderResult(result);
    statusText.textContent = result.ok
      ? "추출 시도가 끝났습니다."
      : `추출 실패: ${result.error ?? "알 수 없는 오류"}`;
  } catch (error) {
    statusText.textContent = error instanceof Error ? error.message : String(error);
  } finally {
    extractButton.disabled = false;
  }
});
