import fs from "node:fs/promises";
import path from "node:path";
import vm from "node:vm";
import { chromium, devices } from "playwright";

const SCRIPT_PATH = path.resolve("category-crawl-site10-test.mjs");
const OUTPUT_DIR = path.resolve("results");
const OUTPUT_JSON = path.join(OUTPUT_DIR, "product-only-category-analysis-2026-05-14.json");
const OUTPUT_MD = path.join(OUTPUT_DIR, "product-only-category-analysis-2026-05-14.md");
const OUTPUT_HTML = path.join(OUTPUT_DIR, "product-only-category-analysis-2026-05-14.html");
const HEADLESS = !/^(false|0|no)$/i.test(process.env.HEADLESS ?? "false");
const WAIT_MS = Number(process.env.RENDER_WAIT_MS ?? 1200);

const DEAD_URLS = new Set([
  "https://www.musinsa.com/app/specialissue/views/428",
  "https://www.musinsa.com/content/1463460902258573717",
]);

const DESKTOP_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

const CATEGORY_KEYWORDS = {
  BEAUTY: ["립", "틴트", "쿠션", "파운데이션", "에센스", "세럼", "앰플", "크림", "로션", "토너", "스킨", "마스크팩", "패드", "선크림", "선케어", "클렌징", "향수", "샴푸", "바디워시", "바디오일", "스크럽", "네일", "뷰티"],
  DIGITAL: ["이어폰", "헤드폰", "키보드", "마우스", "노트북", "갤럭시", "아이폰", "아이패드", "ipad", "monitor", "모니터", "ssd", "케이스", "충전기", "케이블", "태블릿", "스마트워치", "보조배터리", "디바이스"],
  LIVING: ["컵", "머그", "이불", "홑이불", "침구", "베개", "러그", "매트", "수납", "조명", "청소", "커피머신", "테이블", "도자기", "접시", "그릇", "주방", "욕실", "디퓨저", "방향제", "생활가전"],
  FOOD: ["간식", "음료", "커피", "프로틴", "식품", "라면", "과자", "쉐이크", "단백질", "초콜릿", "젤리", "푸드", "생수"],
  HOBBY: ["레고", "피규어", "게임", "취미", "캠핑", "자전거", "보드게임", "퍼즐", "낚시", "등산"],
  SUBSCRIPTION: ["subscription", "멤버십", "정기구독", "월간", "연간 구독", "구독권", "이용권", "기프트카드", "선물카드"],
  FASHION: ["셔츠", "티셔츠", "니트", "knit", "가디건", "팬츠", "데님", "아우터", "자켓", "재킷", "점퍼", "원피스", "스커트", "스니커즈", "신발", "구두", "샌들", "부츠", "가방", "백팩", "숄더백", "bag", "모자", "볼캡", "벨트", "지갑", "양말", "트위드", "부티크", "boutique", "setup", "mini setup"],
};

function extractSites(source) {
  const start = source.indexOf("const SITES = ");
  const arrayStart = source.indexOf("[", start);
  let depth = 0;
  for (let i = arrayStart; i < source.length; i += 1) {
    if (source[i] === "[") depth += 1;
    if (source[i] === "]") depth -= 1;
    if (depth === 0) return vm.runInNewContext(source.slice(arrayStart, i + 1));
  }
  throw new Error("SITES not found");
}

function cleanText(value) {
  return String(value ?? "").replace(/\s+/g, " ").trim();
}

function normalizeImageUrl(value) {
  const cleaned = cleanText(value);
  if (!cleaned || !/^https?:\/\//i.test(cleaned) || /undefined|localhost/i.test(cleaned)) return null;
  return cleaned;
}

function normalizePrice(value) {
  const match = String(value ?? "").match(/\d{1,3}(?:,\d{3})*(?:\.\d+)?/);
  return match?.[0] ?? null;
}

function urlText(url) {
  try {
    const parsed = new URL(url);
    const queryText = [...parsed.searchParams.values()].join(" ");
    return decodeURIComponent(`${parsed.pathname} ${queryText}`).replace(/[-_/=&?]+/g, " ").toLowerCase();
  } catch {
    return "";
  }
}

function normalizeCandidateUrl(value, baseUrl = "https://example.com/") {
  const raw = cleanText(value);
  if (!raw) return null;
  const nested = raw.match(/https?:\/\/[^'")\s<>]+/i)?.[0] ?? raw;
  const unescaped = nested
    .replaceAll("&amp;", "&")
    .replaceAll("\\u0026", "&")
    .replaceAll("\\/", "/");
  try {
    const resolved = new URL(unescaped, baseUrl);
    if (!/^https?:$/i.test(resolved.protocol)) return null;
    return resolved.toString();
  } catch {
    return null;
  }
}

function isProductUrl(url) {
  const normalized = normalizeCandidateUrl(url);
  if (!normalized) return false;
  return /musinsa\.com\/products\/\d+|store\.musinsa\.com\/app\/product\/detail\/|product\.29cm\.co\.kr\/catalog\/\d+|oliveyoung\.co\.kr\/store\/goods\/getGoodsDetail\.do\?goodsNo=|zigzag\.kr\/catalog\/products\/\d+|m\.a-bly\.com\/goods\/\d+|daangn\.com\/kr\/buy-sell\/(?!s\/?$)[^/?#]+\/$|globalbunjang\.com\/product\/\d+/i.test(normalized);
}

function createContext(browser, site) {
  if (site === "에이블리") return browser.newContext({ ...devices["iPhone 13"], locale: "ko-KR" });
  return browser.newContext({
    userAgent: DESKTOP_USER_AGENT,
    viewport: { width: 1440, height: 1200 },
    locale: "ko-KR",
  });
}

function scoreCategory(fields) {
  const scores = Object.fromEntries(Object.keys(CATEGORY_KEYWORDS).map((category) => [category, 0]));
  for (const [textValue, weight] of fields) {
    const text = cleanText(textValue).toLowerCase();
    if (!text) continue;
    for (const [category, keywords] of Object.entries(CATEGORY_KEYWORDS)) {
      for (const keyword of keywords) {
        if (text.includes(keyword)) scores[category] += weight;
      }
    }
  }
  const ranked = Object.entries(scores).sort((a, b) => b[1] - a[1]);
  return ranked[0][1] > 0 ? ranked[0][0] : "ETC";
}

function classifyContentOnly(result) {
  return scoreCategory([
    [result.title, 6],
    [result.ogTitle, 6],
    [result.description, 4],
    [result.bodyPreview, 1],
  ]);
}

function classifyUrlAssisted(result) {
  return scoreCategory([
    [result.title, 6],
    [result.ogTitle, 6],
    [result.description, 4],
    [urlText(result.url), 2],
    [urlText(result.finalUrl), 2],
    [result.bodyPreview, 1],
  ]);
}

async function extractLinks(page, site) {
  return page.evaluate((siteName) => {
    const normalize = (value) => {
      const raw = String(value ?? "").replace(/\s+/g, " ").trim();
      if (!raw) return null;
      const nested = raw.match(/https?:\/\/[^'")\s<>]+/i)?.[0] ?? raw;
      const unescaped = nested.replaceAll("&amp;", "&").replaceAll("\\u0026", "&").replaceAll("\\/", "/");
      try {
        const resolved = new URL(unescaped, location.href);
        if (!/^https?:$/i.test(resolved.protocol)) return null;
        return resolved.toString();
      } catch {
        return null;
      }
    };
    const hrefs = Array.from(document.querySelectorAll("a[href]"))
      .flatMap((node) => [node.getAttribute("href"), node.getAttribute("onclick"), node.outerHTML])
      .map(normalize)
      .filter(Boolean);
    const patterns = {
      "무신사": /musinsa\.com\/products\/\d+/i,
      "29CM": /product\.29cm\.co\.kr\/catalog\/\d+/i,
      "올리브영": /oliveyoung\.co\.kr\/store\/goods\/getGoodsDetail\.do\?goodsNo=/i,
      "지그재그": /zigzag\.kr\/catalog\/products\/\d+/i,
      "에이블리": /m\.a-bly\.com\/goods\/\d+/i,
      "당근": /daangn\.com\/kr\/buy-sell\/(?!s\/?$)[^/?#]+\/$/i,
      "번개장터": /globalbunjang\.com\/product\/\d+/i,
    };
    const pattern = patterns[siteName];
    return [...new Set(hrefs.filter((href) => pattern?.test(href)))].slice(0, 20);
  }, site);
}

async function resolveProductUrl(browser, sample, usedUrls) {
  const directUrl = normalizeCandidateUrl(sample.sourceUrl);
  if (directUrl && isProductUrl(directUrl) && !usedUrls.has(directUrl)) return directUrl;

  const context = await createContext(browser, sample.site);
  const page = await context.newPage();
  try {
    await page.goto(sample.sourceUrl, { waitUntil: "domcontentloaded", timeout: 30_000 });
    if (WAIT_MS > 0) await page.waitForTimeout(WAIT_MS);
    await page.waitForLoadState("networkidle", { timeout: 2000 }).catch(() => {});
    const links = await extractLinks(page, sample.site);
    return links.find((url) => !usedUrls.has(url)) ?? null;
  } catch {
    return null;
  } finally {
    await page.close().catch(() => {});
    await context.close().catch(() => {});
  }
}

async function extractProduct(browser, sample) {
  const context = await createContext(browser, sample.site);
  const page = await context.newPage();
  try {
    const response = await page.goto(sample.url, { waitUntil: "domcontentloaded", timeout: 30_000 });
    if (WAIT_MS > 0) await page.waitForTimeout(WAIT_MS);
    await page.waitForLoadState("networkidle", { timeout: 2000 }).catch(() => {});
    const data = await page.evaluate(() => {
      const clean = (value) => String(value ?? "").replace(/\s+/g, " ").trim();
      const abs = (value) => {
        try { return value ? new URL(value, location.href).toString() : null; } catch { return null; }
      };
      const meta = (selector) => clean(document.querySelector(selector)?.getAttribute("content") ?? "");
      const imageCandidates = Array.from(document.querySelectorAll('meta[property="og:image"], img[src], img[srcset]'))
        .map((node) => {
          const value = node.getAttribute("content") || node.getAttribute("src") || node.getAttribute("srcset") || "";
          const url = abs(value.split(",")[0].trim().split(" ")[0]);
          const rect = typeof node.getBoundingClientRect === "function" ? node.getBoundingClientRect() : { width: 0, height: 0 };
          return { url, width: Number(node.naturalWidth) || Number(node.width) || Math.round(rect.width) || 0, height: Number(node.naturalHeight) || Number(node.height) || Math.round(rect.height) || 0 };
        })
        .filter((item) => item.url && /\.(jpg|jpeg|png|webp)/i.test(item.url))
        .sort((a, b) => (b.width * b.height) - (a.width * a.height));
      const body = clean(document.body?.innerText ?? "");
      const priceLine = body.split(/\n/).map(clean).find((line) => /원/.test(line) && !/배송비|쿠폰|적립|최대/.test(line)) || "";
      return {
        title: clean(document.title),
        ogTitle: meta('meta[property="og:title"]'),
        description: meta('meta[property="og:description"]') || meta('meta[name="description"]'),
        ogImage: meta('meta[property="og:image"]'),
        image: imageCandidates[0]?.url ?? null,
        price: priceLine,
        bodyPreview: body.slice(0, 600),
      };
    });
    const status = response?.status() ?? null;
    const blocked = status === 403 || /잠시만 기다리십시오|access denied|forbidden|captcha|just a moment/i.test(`${data.title} ${data.bodyPreview}`);
    const extractionOk = Boolean(status && status < 400 && !blocked && (data.title || data.ogTitle || data.description || data.image || data.ogImage || data.price));
    const result = {
      ...sample,
      status,
      finalUrl: page.url(),
      title: cleanText(data.ogTitle || data.title),
      browserTitle: cleanText(data.title),
      ogTitle: cleanText(data.ogTitle),
      description: cleanText(data.description),
      image: normalizeImageUrl(data.ogImage) || normalizeImageUrl(data.image),
      price: normalizePrice(data.price),
      bodyPreview: cleanText(data.bodyPreview),
      blocked,
      extractionOk,
    };
    result.contentCategory = extractionOk ? classifyContentOnly(result) : null;
    result.urlAssistedCategory = extractionOk ? classifyUrlAssisted(result) : null;
    result.contentMatched = result.expected === result.contentCategory;
    result.urlAssistedMatched = result.expected === result.urlAssistedCategory;
    return result;
  } catch (error) {
    return {
      ...sample,
      status: null,
      finalUrl: page.url(),
      extractionOk: false,
      blocked: false,
      error: error instanceof Error ? error.message : String(error),
    };
  } finally {
    await page.close().catch(() => {});
    await context.close().catch(() => {});
  }
}

function escapeHtml(value) {
  return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");
}

function groupBy(items, keyFn) {
  const map = new Map();
  for (const item of items) {
    const key = keyFn(item);
    if (!map.has(key)) map.set(key, []);
    map.get(key).push(item);
  }
  return map;
}

function pct(count, total) {
  return total ? `${((count / total) * 100).toFixed(1)}%` : "-";
}

function reports(results, unresolved) {
  const ok = results.filter((item) => item.extractionOk);
  const summary = {
    total: results.length,
    ok: ok.length,
    content: ok.filter((item) => item.contentMatched).length,
    url: ok.filter((item) => item.urlAssistedMatched).length,
  };
  const siteRows = [...groupBy(results, (item) => item.site).entries()].map(([site, rows]) => {
    const siteOk = rows.filter((item) => item.extractionOk);
    return `| ${site} | ${rows.length} | ${siteOk.length}/${rows.length} | ${siteOk.filter((item) => item.contentMatched).length}/${siteOk.length} | ${siteOk.filter((item) => item.urlAssistedMatched).length}/${siteOk.length} |`;
  });
  const issueRows = results
    .filter((item) => !item.extractionOk || !item.urlAssistedMatched)
    .map((item) => `| ${!item.extractionOk ? "추출 실패" : "분류 오답"} | ${item.site} | ${item.expected} | ${item.status ?? "-"} | ${item.contentCategory ?? "-"} | ${item.urlAssistedCategory ?? "-"} | ${String(item.title ?? item.error ?? "-").replaceAll("|", "/")} | ${item.note} | ${item.url} |`);
  const md = [
    "# 상품 상세 URL만 사용한 카테고리 측정",
    "",
    `실행일: ${new Date().toISOString()}`,
    `브라우저: Playwright Chromium ${HEADLESS ? "headless" : "visible/headed"}`,
    `렌더 대기: ${WAIT_MS}ms`,
    "",
    "## 요약",
    "",
    `- 상품 상세 샘플: ${summary.total}`,
    `- 추출 성공: ${summary.ok}/${summary.total} (${pct(summary.ok, summary.total)})`,
    `- weighted content-only 정확도: ${summary.content}/${summary.ok} (${pct(summary.content, summary.ok)})`,
    `- weighted url-assisted 정확도: ${summary.url}/${summary.ok} (${pct(summary.url, summary.ok)})`,
    `- 상품 상세 URL 미확보: ${unresolved.length}`,
    "",
    "## 사이트별",
    "",
    "| 사이트 | 샘플 | 추출 성공 | content-only | url-assisted |",
    "|---|---:|---:|---:|---:|",
    ...siteRows,
    "",
    "## 문제 샘플",
    "",
    "| 구분 | 사이트 | 기대 | HTTP | content-only | url-assisted | 제목/오류 | 비고 | URL |",
    "|---|---|---|---:|---|---|---|---|---|",
    ...issueRows,
    "",
    "## 상품 상세 URL 미확보",
    "",
    "| 사이트 | 기대 | 비고 | 원본 URL |",
    "|---|---|---|---|",
    ...unresolved.map((item) => `| ${item.site} | ${item.expected} | ${item.note} | ${item.sourceUrl} |`),
    "",
  ].join("\n");
  const toRows = (rows) => rows.map((line) => `<tr>${line.split("|").slice(1, -1).map((cell) => `<td>${escapeHtml(cell.trim())}</td>`).join("")}</tr>`).join("\n");
  const unresolvedRows = unresolved.map((item) => `| ${item.site} | ${item.expected} | ${item.note} | ${item.sourceUrl} |`);
  const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>상품 상세 URL 카테고리 측정</title><style>*{box-sizing:border-box}body{margin:0;background:#f7f8fa;color:#1f2933;font-family:Arial,"Malgun Gothic",sans-serif;line-height:1.6}main{max-width:980px;margin:0 auto;padding:28px 20px 56px}section,.hero{background:#fff;border:1px solid #d9dee7;border-radius:8px;padding:18px 20px;margin-bottom:16px}h1{margin:0 0 8px;font-size:25px}h2{margin:0 0 12px;font-size:18px}table{width:100%;border-collapse:collapse;font-size:13.5px}th,td{border-bottom:1px solid #e5e9f0;padding:9px 8px;text-align:left;vertical-align:top;overflow-wrap:anywhere}th{background:#f1f4f8;color:#374151}.metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-top:14px}.metric{border:1px solid #d9dee7;border-radius:8px;padding:12px;background:#fbfcfe}.label{color:#667085;font-size:12px}.value{font-size:21px;font-weight:700;margin-top:4px}.callout{border-left:4px solid #2563eb;background:#f5f9ff;padding:12px 14px;border-radius:0 6px 6px 0}@media(max-width:760px){.metrics{grid-template-columns:1fr}table{font-size:12px}}</style></head><body><main><div class="hero"><h1>상품 상세 URL만 사용한 카테고리 측정</h1><p class="callout">검색/카테고리 URL은 상품 상세 링크를 찾는 데만 사용했고, 최종 분류 평가는 실제 상품 상세 URL만 대상으로 했다.</p><div class="metrics"><div class="metric"><div class="label">추출 성공</div><div class="value">${summary.ok}/${summary.total}</div></div><div class="metric"><div class="label">성공률</div><div class="value">${pct(summary.ok, summary.total)}</div></div><div class="metric"><div class="label">content-only</div><div class="value">${pct(summary.content, summary.ok)}</div></div><div class="metric"><div class="label">url-assisted</div><div class="value">${pct(summary.url, summary.ok)}</div></div></div></div><section><h2>사이트별</h2><table><thead><tr><th>사이트</th><th>샘플</th><th>추출 성공</th><th>content-only</th><th>url-assisted</th></tr></thead><tbody>${toRows(siteRows)}</tbody></table></section><section><h2>문제 샘플</h2><table><thead><tr><th>구분</th><th>사이트</th><th>기대</th><th>HTTP</th><th>content-only</th><th>url-assisted</th><th>제목/오류</th><th>비고</th><th>URL</th></tr></thead><tbody>${toRows(issueRows)}</tbody></table></section><section><h2>상품 상세 URL 미확보</h2><table><thead><tr><th>사이트</th><th>기대</th><th>비고</th><th>원본 URL</th></tr></thead><tbody>${toRows(unresolvedRows)}</tbody></table></section></main></body></html>`;
  return { md, html };
}

await fs.mkdir(OUTPUT_DIR, { recursive: true });
const sourceSamples = extractSites(await fs.readFile(SCRIPT_PATH, "utf8"))
  .flatMap((site) => site.urls.map(([expected, sourceUrl, note], index) => ({
    id: `${site.site}-${index + 1}`,
    site: site.site,
    expected,
    sourceUrl,
    note,
  })))
  .filter((sample) => !DEAD_URLS.has(sample.sourceUrl));

const browser = await chromium.launch({ headless: HEADLESS, slowMo: HEADLESS ? 0 : 40 });
const usedUrls = new Set();
const productSamples = [];
const unresolved = [];

try {
  for (const sample of sourceSamples) {
    const productUrl = await resolveProductUrl(browser, sample, usedUrls);
    if (!productUrl) {
      unresolved.push(sample);
      console.log(`MISS ${sample.site} ${sample.note}`);
      continue;
    }
    usedUrls.add(productUrl);
    productSamples.push({ ...sample, url: productUrl });
    console.log(`LINK ${productSamples.length} ${sample.site} ${sample.note} -> ${productUrl}`);
  }

  const results = [];
  for (let i = 0; i < productSamples.length; i += 1) {
    const result = await extractProduct(browser, productSamples[i]);
    results.push(result);
    console.log(`${i + 1}/${productSamples.length} ${result.extractionOk ? "OK" : "FAIL"} ${result.site} ${result.note} ${result.status ?? "-"} ${result.title ?? result.error ?? ""}`);
  }
  const output = reports(results, unresolved);
  await fs.writeFile(OUTPUT_JSON, `${JSON.stringify({ productSamples, unresolved, results }, null, 2)}\n`);
  await fs.writeFile(OUTPUT_MD, output.md);
  await fs.writeFile(OUTPUT_HTML, output.html);
  console.log(output.md);
} finally {
  await browser.close().catch(() => {});
}
