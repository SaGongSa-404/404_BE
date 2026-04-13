import fs from "node:fs/promises";
import path from "node:path";
import { chromium, devices } from "playwright";

const OUTPUT_DIR = path.resolve("artifacts");

const TARGETS = [
  {
    key: "musinsa",
    label: "무신사",
    url: "https://store.musinsa.com/app/product/detail/478021/0",
    mode: "desktop",
    priceRegexes: [
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
      /"salePrice":\s*([0-9,]+)/i,
      /"normalPrice":\s*([0-9,]+)/i,
    ],
  },
  {
    key: "ably",
    label: "에이블리",
    url: "https://m.a-bly.com/goods/6070447",
    mode: "mobile",
    alternativeUrls: ["https://m.a-bly.com/goods/6070447/install"],
    priceRegexes: [
      /"salePrice":\s*([0-9,]+)/i,
      /"price":\s*([0-9,]+)/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
    ],
    networkHints: [/api\.a-bly\.com\/api\/v3\/goods\/\d+\/basic\//i],
  },
  {
    key: "zigzag",
    label: "지그재그",
    url: "https://zigzag.kr/catalog/products/110621280",
    mode: "desktop",
    priceRegexes: [
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
      /"salePrice":\s*([0-9,]+)/i,
      /"discountedPrice":\s*([0-9,]+)/i,
    ],
  },
  {
    key: "29cm",
    label: "29CM",
    url: "https://product.29cm.co.kr/catalog/2602166",
    mode: "desktop",
    priceRegexes: [
      /"price":\s*([0-9,]+)\s*,\s*"priceCurrency"/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
      /"sell_price":\s*([0-9,]+)/i,
    ],
  },
  {
    key: "oliveyoung",
    label: "올리브영",
    url: "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000230109",
    mode: "desktop",
    priceRegexes: [
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
      /"salePrice":\s*"?([0-9,]+)"?/i,
      /"originalPrice":\s*"?([0-9,]+)"?/i,
    ],
    networkHints: [
      /oliveyoung\.co\.kr\/goods\/api\/v1\/description/i,
      /receiver\.ai\.oliveyoung\.co\.kr\/rest\/logs/i,
      /rts\.ai\.oliveyoung\.co\.kr\/api\/stat/i,
    ],
  },
  {
    key: "coupang",
    label: "쿠팡",
    url: "https://www.coupang.com/vp/products/9280434878",
    mode: "desktop",
    alternativeUrls: ["https://m.coupang.com/nm/products/3013825663"],
    priceRegexes: [
      /"finalPrice":\s*([0-9,]+)/i,
      /"salePrice":\s*([0-9,]+)/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
    ],
  },
  {
    key: "naver-shopping",
    label: "네이버쇼핑",
    url: "https://shopping.naver.com/window-products/style/9612495916",
    mode: "desktop",
    priceRegexes: [
      /"salePrice":\s*([0-9,]+)/i,
      /"lowPrice":\s*([0-9,]+)/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
    ],
  },
  {
    key: "daangn",
    label: "당근마켓",
    url: "https://www.daangn.com/kr/buy-sell/%EC%98%A4%EB%A5%B4%ED%85%8C-%EC%BB%A4%ED%94%BC%EB%A8%B8%EC%8B%A0-ock-352b-r5fvp77gm767/",
    mode: "desktop",
    priceRegexes: [
      /"price":"?([0-9,.]+)"?/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
    ],
  },
  {
    key: "bunjang",
    label: "번개장터",
    url: "https://globalbunjang.com/product/346091145",
    mode: "desktop",
    priceRegexes: [
      /"price":"?([0-9,.]+)"?/i,
      /product:price:amount["'][^>]*content=["']([^"']+)["']/i,
    ],
  },
];

const DESKTOP_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

function cleanText(value) {
  return value?.replace(/\s+/g, " ").trim() || null;
}

function normalizePrice(value) {
  if (!value) return null;
  const trimmed = value.trim().replace(/,$/, "");
  return trimmed || null;
}

function normalizeImageUrl(value) {
  const cleaned = cleanText(value);
  if (!cleaned) return null;
  if (!/^https?:\/\//i.test(cleaned)) return null;
  if (/undefined/i.test(cleaned)) return null;
  if (/localhost/i.test(cleaned)) return null;
  return cleaned;
}

function extractMeta(html, name) {
  const propertyPatterns = [
    new RegExp(
      `<meta[^>]+property=["']${name}["'][^>]+content=["']([^"']+)["']`,
      "i",
    ),
    new RegExp(
      `<meta[^>]+content=["']([^"']+)["'][^>]+property=["']${name}["']`,
      "i",
    ),
  ];

  for (const pattern of propertyPatterns) {
    const match = html.match(pattern);
    if (match) {
      return name === "og:image"
        ? normalizeImageUrl(match[1])
        : cleanText(match[1]);
    }
  }

  return null;
}

function extractTitle(html) {
  const match = html.match(/<title[^>]*>(.*?)<\/title>/is);
  return match ? cleanText(match[1]) : null;
}

function extractPrice(html, regexes) {
  for (const regex of regexes) {
    const match = html.match(regex);
    if (match?.[1]) return normalizePrice(match[1]);
  }

  return null;
}

function extractVisiblePrices(text) {
  const matches = text.match(/\d{1,3}(?:,\d{3})+(?:\.\d+)?원/g) || [];
  return [...new Set(matches)].slice(0, 5);
}

function looksInterestingUrl(url, hints = []) {
  return hints.some((hint) => hint.test(url));
}

function sniffPriceAndImage(text) {
  if (!text) {
    return { price: null, image: null };
  }

  let decoded = text;
  try {
    decoded = decodeURIComponent(text);
  } catch {
    decoded = text;
  }

  const priceMatch =
    decoded.match(/"salePrice"\s*:\s*"?([0-9,]+)"?/i) ||
    decoded.match(/"originalPrice"\s*:\s*"?([0-9,]+)"?/i) ||
    decoded.match(/"price"\s*:\s*"?([0-9,]+(?:\.\d+)?)"?/i) ||
    decoded.match(/\d{1,3}(?:,\d{3})+(?:\.\d+)?원/);

  const imageMatch =
    decoded.match(/https?:\/\/[^"' ]+\.(?:jpg|jpeg|png|webp)(?:\?[^"' ]*)?/i) ||
    decoded.match(/"imageUrl"\s*:\s*"([^"]+)"/i);

  return {
    price: normalizePrice(priceMatch?.[1] ?? priceMatch?.[0] ?? null),
    image: normalizeImageUrl(imageMatch?.[1] ?? imageMatch?.[0] ?? null),
  };
}

async function fetchStaticUrl(url, target) {
  const result = {
    ok: false,
    status: null,
    finalUrl: null,
    title: null,
    ogImage: null,
    price: null,
    error: null,
    sourceUrl: url,
  };

  try {
    const response = await fetch(url, {
      headers: {
        "user-agent": DESKTOP_USER_AGENT,
        "accept-language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
      },
      redirect: "follow",
    });

    result.status = response.status;
    result.finalUrl = response.url;

    const html = await response.text();
    result.title = extractTitle(html);
    result.ogImage = extractMeta(html, "og:image");
    result.price = extractPrice(html, target.priceRegexes);
    result.ok = response.ok;

    if (!response.ok) {
      result.error = `HTTP ${response.status}`;
    }
  } catch (error) {
    result.error = error instanceof Error ? error.message : String(error);
  }

  return result;
}

async function fetchStatic(target) {
  const urls = [target.url, ...(target.alternativeUrls ?? [])];
  const attempts = [];

  for (const url of urls) {
    const attempt = await fetchStaticUrl(url, target);
    attempts.push(attempt);
    if (attempt.price || attempt.ogImage) {
      break;
    }
  }

  const best =
    attempts.find((attempt) => attempt.price && attempt.ogImage) ||
    attempts.find((attempt) => attempt.price || attempt.ogImage) ||
    attempts[0];

  return { best, attempts };
}

async function createContext(browser, mode) {
  if (mode === "mobile") {
    return browser.newContext({
      ...devices["iPhone 13"],
      locale: "ko-KR",
    });
  }

  return browser.newContext({
    userAgent: DESKTOP_USER_AGENT,
    viewport: { width: 1440, height: 1200 },
    locale: "ko-KR",
  });
}

async function fetchWithBrowser(browser, target) {
  const context = await createContext(browser, target.mode);
  const page = await context.newPage();
  const networkEntries = [];
  const candidateUrls = [target.url, ...(target.alternativeUrls ?? [])];

  const captureResponse = async (response) => {
    const url = response.url();
    const hintMatched = looksInterestingUrl(url, target.networkHints ?? []);
    const maybeInteresting =
      hintMatched || /(api|graphql|goods|product|price|catalog|item)/i.test(url);

    if (!maybeInteresting) return;

    const contentType = response.headers()["content-type"] || "";
    const entry = {
      url,
      status: response.status(),
      resourceType: response.request().resourceType(),
      contentType,
      price: null,
      image: null,
      snippet: null,
    };

    if (/json|javascript|html|plain/i.test(contentType) || hintMatched) {
      try {
        const text = await response.text();
        const sniffed = sniffPriceAndImage(text);
        entry.price = sniffed.price;
        entry.image = sniffed.image;
        entry.snippet = cleanText(text)?.slice(0, 260) ?? null;
      } catch {
        entry.snippet = null;
      }
    } else {
      const sniffed = sniffPriceAndImage(url);
      entry.price = sniffed.price;
      entry.image = sniffed.image;
    }

    networkEntries.push(entry);
  };

  page.on("response", (response) => {
    void captureResponse(response);
  });

  const attempts = [];

  try {
    for (const candidateUrl of candidateUrls) {
      const attempt = {
        sourceUrl: candidateUrl,
        ok: false,
        status: null,
        finalUrl: null,
        title: null,
        ogImage: null,
        pagePrice: null,
        visiblePrices: [],
        networkPrice: null,
        networkImage: null,
        screenshot: null,
        error: null,
      };

      try {
        const response = await page.goto(candidateUrl, {
          waitUntil: "domcontentloaded",
          timeout: 30_000,
        });

        await page.waitForTimeout(4_000);

        const html = await page.content();
        const bodyText = cleanText(await page.locator("body").innerText());
        const matchedNetwork = networkEntries.filter((entry) =>
          looksInterestingUrl(entry.url, target.networkHints ?? []),
        );

        attempt.status = response?.status() ?? null;
        attempt.finalUrl = page.url();
        attempt.title = await page.title();
        attempt.ogImage = normalizeImageUrl(
          await page
            .locator('meta[property="og:image"]')
            .first()
            .getAttribute("content")
            .catch(() => null),
        );
        attempt.pagePrice = extractPrice(html, target.priceRegexes);
        attempt.visiblePrices = extractVisiblePrices(bodyText ?? "");
        attempt.networkPrice =
          matchedNetwork.find((entry) => entry.price)?.price ??
          networkEntries.find((entry) => entry.price)?.price ??
          null;
        attempt.networkImage =
          matchedNetwork.find((entry) => entry.image)?.image ??
          networkEntries.find((entry) => entry.image)?.image ??
          null;
        attempt.ok = Boolean(attempt.status && attempt.status < 400);

        const screenshotPath = path.join(
          OUTPUT_DIR,
          `${target.key}-${attempts.length + 1}.png`,
        );
        await page.screenshot({ path: screenshotPath, fullPage: true });
        attempt.screenshot = screenshotPath;
      } catch (error) {
        attempt.error = error instanceof Error ? error.message : String(error);
      }

      attempts.push(attempt);

      if (
        attempt.pagePrice ||
        attempt.visiblePrices.length > 0 ||
        attempt.networkPrice ||
        attempt.ogImage ||
        attempt.networkImage
      ) {
        break;
      }
    }
  } finally {
    await page.close();
    await context.close();
  }

  const best =
    attempts.find(
      (attempt) =>
        (attempt.pagePrice || attempt.visiblePrices.length > 0 || attempt.networkPrice) &&
        (attempt.ogImage || attempt.networkImage),
    ) ||
    attempts.find(
      (attempt) =>
        attempt.pagePrice ||
        attempt.visiblePrices.length > 0 ||
        attempt.networkPrice ||
        attempt.ogImage ||
        attempt.networkImage,
    ) ||
    attempts[0];

  return {
    best,
    attempts,
    networkEntries: networkEntries
      .filter((entry) => entry.price || entry.image || looksInterestingUrl(entry.url, target.networkHints ?? []))
      .slice(0, 25),
  };
}

function summarize(target, staticResult, browserResult) {
  const staticBest = staticResult.best;
  const browserBest = browserResult.best;

  const extractedPrice =
    staticBest?.price ??
    browserBest?.pagePrice ??
    browserBest?.networkPrice ??
    browserBest?.visiblePrices?.[0] ??
    null;

  const extractedImage =
    staticBest?.ogImage ??
    browserBest?.ogImage ??
    browserBest?.networkImage ??
    null;

  const methodsSucceeded = [];
  if (staticBest?.price || staticBest?.ogImage) methodsSucceeded.push("static");
  if (browserBest?.pagePrice || browserBest?.visiblePrices?.length) methodsSucceeded.push("browser_dom");
  if (browserBest?.networkPrice || browserBest?.networkImage) methodsSucceeded.push("browser_network");
  if (browserBest?.screenshot) methodsSucceeded.push("screenshot");

  let verdict = "불가";
  if (extractedPrice && extractedImage) {
    verdict = "높음";
  } else if (extractedPrice || extractedImage) {
    verdict = "조건부";
  }

  const recommendedStrategy = methodsSucceeded.includes("static")
    ? "정적 HTML 메타/JSON-LD 추출"
    : methodsSucceeded.includes("browser_network")
      ? "브라우저 네트워크 응답 추출"
      : methodsSucceeded.includes("browser_dom")
        ? "브라우저 렌더링 후 DOM/본문 추출"
        : methodsSucceeded.includes("screenshot")
          ? "스크린샷 수동 확인"
          : "대응 전략 없음";

  return {
    service: target.label,
    key: target.key,
    url: target.url,
    verdict,
    recommendedStrategy,
    extractedPrice,
    extractedImage,
    methodsSucceeded,
    static: staticResult,
    browser: browserResult,
  };
}

async function main() {
  await fs.mkdir(OUTPUT_DIR, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const results = [];

  try {
    for (const target of TARGETS) {
      console.log(`\n[${target.label}] 검증 중...`);
      const staticResult = await fetchStatic(target);
      const browserResult = await fetchWithBrowser(browser, target);
      const summary = summarize(target, staticResult, browserResult);
      results.push(summary);

      console.log(
        JSON.stringify(
          {
            service: summary.service,
            verdict: summary.verdict,
            recommendedStrategy: summary.recommendedStrategy,
            extractedPrice: summary.extractedPrice,
            extractedImage: summary.extractedImage,
            methodsSucceeded: summary.methodsSucceeded,
            static: {
              status: staticResult.best?.status ?? null,
              sourceUrl: staticResult.best?.sourceUrl ?? null,
              price: staticResult.best?.price ?? null,
              ogImage: staticResult.best?.ogImage ?? null,
              error: staticResult.best?.error ?? null,
            },
            browser: {
              status: browserResult.best?.status ?? null,
              sourceUrl: browserResult.best?.sourceUrl ?? null,
              pagePrice: browserResult.best?.pagePrice ?? null,
              visiblePrices: browserResult.best?.visiblePrices ?? [],
              networkPrice: browserResult.best?.networkPrice ?? null,
              ogImage: browserResult.best?.ogImage ?? null,
              networkImage: browserResult.best?.networkImage ?? null,
              error: browserResult.best?.error ?? null,
              screenshot: browserResult.best?.screenshot ?? null,
            },
          },
          null,
          2,
        ),
      );
    }
  } finally {
    await browser.close();
  }

  const jsonPath = path.join(OUTPUT_DIR, "shopping-validation-results.json");
  await fs.writeFile(jsonPath, `${JSON.stringify(results, null, 2)}\n`, "utf8");

  const lines = [
    "# Shopping Link Validation",
    "",
    `Generated at: ${new Date().toISOString()}`,
    "",
    "| 서비스 | 판정 | 추천 전략 | 추출 가격 | 추출 이미지 | 성공 방식 |",
    "| --- | --- | --- | --- | --- | --- |",
  ];

  for (const result of results) {
    lines.push(
      `| ${result.service} | ${result.verdict} | ${result.recommendedStrategy} | ${
        result.extractedPrice ?? "-"
      } | ${result.extractedImage ? "yes" : "no"} | ${
        result.methodsSucceeded.join(", ") || "-"
      } |`,
    );
  }

  const mdPath = path.join(OUTPUT_DIR, "shopping-validation-results.md");
  await fs.writeFile(mdPath, `${lines.join("\n")}\n`, "utf8");

  console.log(`\n결과 저장: ${jsonPath}`);
  console.log(`결과 저장: ${mdPath}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
