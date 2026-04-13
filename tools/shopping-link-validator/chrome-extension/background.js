chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type === "CAPTURE_VISIBLE_TAB") {
    const windowId = sender.tab?.windowId;
    chrome.tabs.captureVisibleTab(
      windowId,
      { format: "png" },
      (dataUrl) => {
        if (chrome.runtime.lastError) {
          sendResponse({
            ok: false,
            error: chrome.runtime.lastError.message
          });
          return;
        }

        const filename = `coupang-capture-${Date.now()}.png`;
        chrome.downloads.download({
          url: dataUrl,
          filename,
          saveAs: true
        }, (downloadId) => {
          if (chrome.runtime.lastError) {
            sendResponse({
              ok: false,
              error: chrome.runtime.lastError.message
            });
            return;
          }

          sendResponse({
            ok: true,
            filename,
            downloadId
          });
        });
      }
    );

    return true;
  }
});
