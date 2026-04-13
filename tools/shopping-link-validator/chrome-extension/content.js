chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === "COUPANG_EXTRACT") {
    try {
      const result = window.CoupangExtractor.extractProductData();
      sendResponse({ ok: true, result });
    } catch (error) {
      sendResponse({
        ok: false,
        error: error instanceof Error ? error.message : String(error)
      });
    }

    return true;
  }

  if (message?.type === "COUPANG_HIGHLIGHT_PRICE") {
    const priceSelectors = [
      "[class*='total-price']",
      "[class*='prod-sale-price']",
      "[class*='price'] strong",
      "[class*='price']"
    ];

    for (const selector of priceSelectors) {
      const node = document.querySelector(selector);
      if (!node) continue;
      node.scrollIntoView({ behavior: "smooth", block: "center" });
      node.style.outline = "3px solid #ff6a00";
      node.style.outlineOffset = "4px";
      setTimeout(() => {
        node.style.outline = "";
        node.style.outlineOffset = "";
      }, 3000);
      sendResponse({ ok: true, selector });
      return true;
    }

    sendResponse({ ok: false, error: "No likely price node found." });
    return true;
  }
});
