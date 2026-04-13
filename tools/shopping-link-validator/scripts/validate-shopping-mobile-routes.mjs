import fs from "node:fs/promises";
import path from "node:path";
import { chromium, devices } from "playwright";

const OUTPUT_DIR = path.resolve("artifacts");

const TARGETS = [
  {
    key: "musinsa-mobile",
    label: "무신사",
    urls: ["https://www.musinsa.com/products/478021"],
    priceRegexes: [
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
      /"salePrice":\s*([0-9,]+)/i,
      /"normalPrice":\s*([0-9,]+)/i,
    ],
  },
  {
    key: "ably-mobile",
    label: "에이블리",
    urls: ["https://m.a-bly.com/goods/6070447", "https://m.a-bly.com/goods/6070447/install"],
    priceRegexes: [
      /"salePrice":\s*([0-9,]+)/i,
      /"price":\s*([0-9,]+)/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
    ],
  },
  {
    key: "zigzag-mobile",
    label: "지그재그",
    urls: ["https://zigzag.kr/catalog/products/110621280"],
    priceRegexes: [
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
      /"salePrice":\s*([0-9,]+)/i,
      /"discountedPrice":\s*([0-9,]+)/i,
    ],
  },
  {
    key: "29cm-mobile",
    label: "29CM",
    urls: ["https://m.29cm.co.kr/products/2602166", "https://product.29cm.co.kr/catalog/2602166"],
    priceRegexes: [
      /"price":\s*([0-9,]+)\s*,\s*"priceCurrency"/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
      /"sell_price":\s*([0-9,]+)/i,
    ],
  },
  {
    key: "oliveyoung-mobile",
    label: "올리브영",
    urls: ["https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000230109"],
    priceRegexes: [
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
      /"salePrice":\s*"?([0-9,]+)"?/i,
      /"originalPrice":\s*"?([0-9,]+)"?/i,
    ],
  },
  {
    key: "coupang-mobile",
    label: "쿠팡",
    urls: ["https://m.coupang.com/nm/products/3013825663"],
    priceRegexes: [
      /\d{1,3}(?:,\d{3})+원/,
      /"price":\s*"?([0-9,]+)"?/i,
    ],
  },
  {
    key: "naver-mobile",
    label: "네이버쇼핑",
    urls: ["https://shopping.naver.com/window-products/style/9612495916"],
    priceRegexes: [
      /"salePrice":\s*([0-9,]+)/i,
      /"lowPrice":\s*([0-9,]+)/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
    ],
  },
  {
    key: "daangn-mobile",
    label: "당근마켓",
    urls: ["https://www.daangn.com/kr/buy-sell/%EC%98%A4%EB%A5%B4%ED%85%8C-%EC%BB%A4%ED%94%BC%EB%A8%B8%EC%8B%A0-ock-352b-r5fvp77gm767/"],
    priceRegexes: [
      /"price":"?([0-9,.]+)"?/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
    ],
  },
  {
    key: "bunjang-mobile",
    label: "번개장터",
    urls: ["https://globalbunjang.com/product/346091145"],
    priceRegexes: [
      /"price":"?([0-9,.]+)"?/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
    ],
  },
];

function cleanText(value) {
  return value?.replace(/\s+/g, " ").trim() || null;
}

function normalizePrice(value) {
  if (!value) return null;
  const matched = String(value).match(/\d[\d,]*(?:\.\d+)?/);
  return matched ? matched[0] : null;
}

function normalizeImageUrl(value) {
  const cleaned = cleanText(value);
  if (!cleaned) return null;
  if (!/^https?:\/\//i.test(cleaned)) return null;
  if (/undefined|localhost/i.test(cleaned)) return null;
  return cleaned;
}

function extractTitle(html, bodyText) {
  const titleMatch = html.match(/<title[^>]*>(.*?)<\/title>/is);
  if (titleMatch?.[1]) return cleanText(titleMatch[1]);

  const bodyLine = bodyText
    .split("\n")
    .map((line) => cleanText(line))
    .find((line) => line && !/검색|장바구니|쿠폰|로그인|공유|찜/.test(line));

  return bodyLine || null;
}

function extractOgImage(html) {
  const patterns = [
    /<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']/i,
    /<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']/i,
  ];

  for (const pattern of patterns) {
    const match = html.match(pattern);
    if (match?.[1]) return normalizeImageUrl(match[1]);
  }

  return null;
}

function extractPrice(html, bodyText, regexes) {
  for (const regex of regexes) {
    const match = html.match(regex) || bodyText.match(regex);
    const value = match?.[1] ?? match?.[0];
    const normalized = normalizePrice(value);
    if (normalized) return normalized;
  }

  const bodyMatch = bodyText.match(/\d{1,3}(?:,\d{3})+원/);
  return normalizePrice(bodyMatch?.[0]);
}

function findBodyImage(page) {
  return page.evaluate(() => {
    const images = Array.from(document.images)
      .map((img) => {
        const src = img.currentSrc || img.src || "";
        const width = img.naturalWidth || img.width || 0;
        const height = img.naturalHeight || img.height || 0;
        let score = 0;
        if (/vendorItemPackage|image\/product|thumb\/mhigh|thumb\/q/.test(src)) score += 50;
        if (/coupangcdn|msscdn|oliveyoung|pstatic|karroter|29cm|zigzag|cloudfront|bunjang|daangn/i.test(src)) score += 20;
        if (/icon|logo|banner|badge|sprite|mypromotion|displayitem/.test(src)) score -= 40;
        score += Math.min(width, 2000) / 40;
        score += Math.min(height, 2000) / 40;
        return { src, width, height, score };
      })
      .filter((item) => /^https?:\/\//.test(item.src))
      .sort((a, b) => b.score - a.score);

    return images[0] || null;
  });
}

async function testUrl(browser, target, url) {
  const context = await browser.newContext({
    ...devices["iPhone 13"],
    locale: "ko-KR",
  });
  const page = await context.newPage();

  try {
    const response = await page.goto(url, {
      waitUntil: "domcontentloaded",
      timeout: 30_000,
    });

    await page.waitForTimeout(3500);

    const html = await page.content();
    const bodyText = await page.locator("body").innerText().catch(() => "");
    const bodyImage = await findBodyImage(page).catch(() => null);
    const title = extractTitle(html, bodyText);
    const price = extractPrice(html, bodyText, target.priceRegexes);
    const ogImage = extractOgImage(html);
    const image = ogImage || bodyImage?.src;
    const screenshotPath = path.join(OUTPUT_DIR, `${target.key}-${Date.now()}.png`);
    await page.screenshot({ path: screenshotPath, fullPage: true });

    return {
      ok: Boolean((response?.status() ?? 0) < 400),
      status: response?.status() ?? null,
      sourceUrl: url,
      finalUrl: page.url(),
      title,
      price,
      image,
      ogImage,
      bodyImage,
      screenshotPath,
    };
  } catch (error) {
    return {
      ok: false,
      status: null,
      sourceUrl: url,
      finalUrl: page.url(),
      title: null,
      price: null,
      image: null,
      error: error instanceof Error ? error.message : String(error),
    };
  } finally {
    await page.close();
    await context.close();
  }
}

async function main() {
  await fs.mkdir(OUTPUT_DIR, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const results = [];

  try {
    for (const target of TARGETS) {
      console.log(`\n[${target.label}] 모바일 경로 검증 중...`);
      let chosen = null;
      const attempts = [];

      for (const url of target.urls) {
        const attempt = await testUrl(browser, target, url);
        attempts.push(attempt);
        if (attempt.price && attempt.image) {
          chosen = attempt;
          break;
        }
      }

      const best = chosen || attempts.find((item) => item.price || item.image) || attempts[0];
      const verdict = best.price && best.image ? "모바일 가능" : best.price || best.image ? "조건부" : "어려움";
      const summary = {
        service: target.label,
        verdict,
        best,
        attempts,
      };
      results.push(summary);
      console.log(JSON.stringify(summary, null, 2));
    }
  } finally {
    await browser.close();
  }

  const jsonPath = path.join(OUTPUT_DIR, "shopping-mobile-route-results.json");
  await fs.writeFile(jsonPath, `${JSON.stringify(results, null, 2)}\n`, "utf8");

  const lines = [
    "# Shopping Mobile Route Validation",
    "",
    `Generated at: ${new Date().toISOString()}`,
    "",
    "| 서비스 | 판정 | 가격 | 이미지 | 사용 URL |",
    "| --- | --- | --- | --- | --- |",
  ];

  for (const result of results) {
    lines.push(
      `| ${result.service} | ${result.verdict} | ${result.best.price ?? "-"} | ${
        result.best.image ? "yes" : "no"
      } | ${result.best.sourceUrl} |`,
    );
  }

  const mdPath = path.join(OUTPUT_DIR, "shopping-mobile-route-results.md");
  await fs.writeFile(mdPath, `${lines.join("\n")}\n`, "utf8");

  console.log(`\n결과 저장: ${jsonPath}`);
  console.log(`결과 저장: ${mdPath}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
