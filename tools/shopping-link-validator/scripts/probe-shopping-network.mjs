import { chromium } from "playwright";

const USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

const TARGETS = [
  {
    label: "에이블리 install",
    url: "https://m.a-bly.com/goods/41301800/install",
  },
  {
    label: "올리브영 valid",
    url: "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000230109",
  },
  {
    label: "쿠팡 alt sample",
    url: "https://www.coupang.com/vp/products/9280434878",
  },
];

function looksInteresting(url) {
  return /(api|graphql|goods|product|catalog|price|item|offer|search)/i.test(url);
}

function truncate(value, size = 220) {
  if (!value) return value;
  return value.length > size ? `${value.slice(0, size)}...` : value;
}

async function probeTarget(browser, target) {
  const page = await browser.newPage({
    userAgent: USER_AGENT,
    viewport: { width: 1440, height: 1100 },
    locale: "ko-KR",
  });

  const network = [];

  page.on("response", async (response) => {
    const url = response.url();
    if (!looksInteresting(url)) return;

    const contentType = response.headers()["content-type"] || "";
    const entry = {
      status: response.status(),
      resourceType: response.request().resourceType(),
      url,
      contentType,
      text: null,
    };

    if (/json|javascript|html/i.test(contentType)) {
      try {
        entry.text = truncate(await response.text());
      } catch {
        entry.text = null;
      }
    }

    network.push(entry);
  });

  try {
    const response = await page.goto(target.url, {
      waitUntil: "domcontentloaded",
      timeout: 30_000,
    });

    await page.waitForTimeout(5000);

    const bodyText = truncate(await page.locator("body").innerText());

    console.log(`\n=== ${target.label} ===`);
    console.log(
      JSON.stringify(
        {
          status: response?.status() ?? null,
          finalUrl: page.url(),
          title: await page.title(),
          bodyText,
          network: network.slice(0, 25),
        },
        null,
        2,
      ),
    );
  } finally {
    await page.close();
  }
}

const browser = await chromium.launch({ headless: true });
try {
  for (const target of TARGETS) {
    await probeTarget(browser, target);
  }
} finally {
  await browser.close();
}
