import { app, BrowserWindow, ipcMain } from "electron";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, "..");
const artifactsDir = path.join(projectRoot, "artifacts");
const extractorPath = path.join(projectRoot, "chrome-extension", "extractor.js");

const DESKTOP_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
const MOBILE_UA =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

app.commandLine.appendSwitch("disable-blink-features", "AutomationControlled");

let extractorSourcePromise;

async function getExtractorSource() {
  if (!extractorSourcePromise) {
    extractorSourcePromise = fs.readFile(extractorPath, "utf8");
  }
  return extractorSourcePromise;
}

function createShellWindow() {
  const win = new BrowserWindow({
    width: 1040,
    height: 860,
    backgroundColor: "#f8f2ea",
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  void win.loadFile(path.join(__dirname, "renderer.html"));
  return win;
}

async function createProbeWindow(mode = "desktop") {
  const probe = new BrowserWindow({
    show: false,
    width: mode === "mobile" ? 430 : 1440,
    height: mode === "mobile" ? 932 : 1200,
    webPreferences: {
      partition: `temp:coupang-probe-${Date.now()}-${Math.random()}`,
      sandbox: false,
      contextIsolation: false,
      nodeIntegration: false,
      backgroundThrottling: false,
    },
  });

  probe.webContents.setUserAgent(mode === "mobile" ? MOBILE_UA : DESKTOP_UA);
  return probe;
}

async function extractFromUrl(url, mode = "desktop") {
  await fs.mkdir(artifactsDir, { recursive: true });

  const probe = await createProbeWindow(mode);
  const startedAt = new Date().toISOString();
  let mainFrameStatus = null;
  let mainFrameError = null;

  const onCompleted = (details) => {
    if (details.webContentsId !== probe.webContents.id) return;
    if (details.resourceType !== "mainFrame") return;
    mainFrameStatus = details.statusCode;
  };

  const onErrorOccurred = (details) => {
    if (details.webContentsId !== probe.webContents.id) return;
    if (details.resourceType !== "mainFrame") return;
    mainFrameError = details.error;
  };

  probe.webContents.session.webRequest.onCompleted({ urls: ["<all_urls>"] }, onCompleted);
  probe.webContents.session.webRequest.onErrorOccurred({ urls: ["<all_urls>"] }, onErrorOccurred);

  try {
    await probe.webContents.loadURL(url, {
      userAgent: mode === "mobile" ? MOBILE_UA : DESKTOP_UA,
      extraHeaders: "Accept-Language: ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7\n",
    });

    await new Promise((resolve) => setTimeout(resolve, 3500));

    const extractorSource = await getExtractorSource();
    const result = await probe.webContents.executeJavaScript(
      `${extractorSource}\nwindow.CoupangExtractor.extractProductData();`,
      true,
    );

    const title = probe.getTitle();
    const bodyText = await probe.webContents.executeJavaScript(
      "(document.body && document.body.innerText ? document.body.innerText : '').slice(0, 1200)",
      true,
    );

    const image = await probe.webContents.capturePage();
    const fileName = `coupang-app-prototype-${Date.now()}.png`;
    const screenshotPath = path.join(artifactsDir, fileName);
    await fs.writeFile(screenshotPath, image.toPNG());

    return {
      ok: true,
      startedAt,
      finishedAt: new Date().toISOString(),
      httpStatus: mainFrameStatus,
      finalUrl: probe.webContents.getURL(),
      title,
      bodyPreview: bodyText,
      screenshotPath,
      extraction: result,
      mode,
      blocked:
        mainFrameStatus === 403 ||
        /Access Denied/i.test(title) ||
        /Access Denied/i.test(bodyText),
    };
  } catch (error) {
    const screenshotPath = path.join(
      artifactsDir,
      `coupang-app-prototype-error-${Date.now()}.png`,
    );

    try {
      const image = await probe.webContents.capturePage();
      await fs.writeFile(screenshotPath, image.toPNG());
    } catch {
      // Ignore screenshot failure when the page never painted.
    }

    return {
      ok: false,
      startedAt,
      finishedAt: new Date().toISOString(),
      httpStatus: mainFrameStatus,
      finalUrl: probe.webContents.getURL(),
      screenshotPath,
      networkError: mainFrameError,
      error: error instanceof Error ? error.message : String(error),
    };
  } finally {
    probe.destroy();
  }
}

ipcMain.handle("coupang:extract", async (_event, url) => {
  return extractFromUrl(url);
});

app.whenReady().then(async () => {
  const testUrl = process.env.COUPANG_TEST_URL;
  const testMode = process.env.COUPANG_TEST_MODE || "desktop";
  if (testUrl) {
    const result = await extractFromUrl(testUrl, testMode);
    console.log(JSON.stringify(result, null, 2));
    app.quit();
    return;
  }

  createShellWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createShellWindow();
    }
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});
