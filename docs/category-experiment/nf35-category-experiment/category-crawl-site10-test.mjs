import fs from "node:fs/promises";
import path from "node:path";
import { chromium, devices } from "playwright";

const OUTPUT_DIR = path.resolve("results");
const OUTPUT_JSON = path.join(OUTPUT_DIR, "category-crawl-site10-test-2026-05-14.json");
const OUTPUT_MD = path.join(OUTPUT_DIR, "category-crawl-site10-test-2026-05-14.md");
const BROWSER_WAIT_MS = Number(process.env.BROWSER_WAIT_MS ?? 3_000);

const SITES = [
  {
    site: "무신사",
    urls: [
      ["FASHION", "https://www.musinsa.com/products/478021", "기존 상품 상세 성공 샘플"],
      ["FASHION", "https://www.musinsa.com/search/goods?keyword=%ED%8C%AC%EC%B8%A0", "검색: 팬츠"],
      ["FASHION", "https://www.musinsa.com/content/cms/7421", "셔츠/팬츠/스니커즈"],
      ["FASHION", "https://www.musinsa.com/content/cms/8131", "데님 팬츠/셔츠/스니커즈"],
      ["FASHION", "https://www.musinsa.com/search/goods?keyword=%EC%9B%90%ED%94%BC%EC%8A%A4", "검색: 원피스"],
      ["FASHION", "https://www.musinsa.com/content/1494203540812634972", "스니커즈"],
      ["FASHION", "https://www.musinsa.com/content/mz/65412", "스니커즈"],
      ["FASHION", "https://www.musinsa.com/content/mz/105496", "스니커즈"],
      ["FASHION", "https://www.musinsa.com/search/goods?keyword=%EC%85%94%EC%B8%A0", "검색: 셔츠"],
      ["FASHION", "https://www.musinsa.com/search/goods?keyword=%EA%B0%80%EB%B0%A9", "검색: 가방"],
    ],
  },
  {
    site: "29CM",
    urls: [
      ["FASHION", "https://product.29cm.co.kr/catalog/2602166", "기존 상품 상세 성공 샘플"],
      ["FASHION", "https://product.29cm.co.kr/catalog/3053461", "니트"],
      ["FASHION", "https://product.29cm.co.kr/catalog/1584496", "패션 소품"],
      ["FASHION", "https://product.29cm.co.kr/catalog/1604960", "백팩"],
      ["LIVING", "https://product.29cm.co.kr/catalog/364149?id=3264999", "침구"],
      ["LIVING", "https://product.29cm.co.kr/catalog/2860848?id=954390", "도자기/접시"],
      ["LIVING", "https://search.29cm.co.kr/?keyword=%EC%BB%A4%ED%94%BC%EB%A8%B8%EC%8B%A0", "검색: 커피머신"],
      ["DIGITAL", "https://search.29cm.co.kr/?keyword=%ED%82%A4%EB%B3%B4%EB%93%9C", "검색: 키보드"],
      ["HOBBY", "https://search.29cm.co.kr/?keyword=%EC%BA%A0%ED%95%91", "검색: 캠핑"],
      ["ETC", "https://search.29cm.co.kr/?keyword=%EC%84%A0%EB%AC%BC%EC%B9%B4%EB%93%9C", "검색: 선물카드"],
    ],
  },
  {
    site: "올리브영",
    urls: [
      ["BEAUTY", "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000230109", "기존 상품 상세 성공 샘플"],
      ["BEAUTY", "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000145662", "크림"],
      ["BEAUTY", "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000137482", "크림"],
      ["BEAUTY", "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000228412", "패드"],
      ["BEAUTY", "https://www.oliveyoung.co.kr/store/display/getCategoryShop.do?dispCatNo=10000010001", "스킨케어"],
      ["BEAUTY", "https://www.oliveyoung.co.kr/store/display/getCategoryShop.do?dispCatNo=10000010003", "바디케어"],
      ["BEAUTY", "https://www.oliveyoung.co.kr/store/display/getCategoryShop.do?dispCatNo=10000010011", "선케어"],
      ["BEAUTY", "https://www.oliveyoung.co.kr/store/display/getBrandShopDetail.do?dispCatNo=90000020057&onlBrndCd=A000754", "샴푸/바디오일"],
      ["FOOD", "https://www.oliveyoung.co.kr/store/display/getCategoryShop.do?dispCatNo=10000020002", "푸드"],
      ["DIGITAL", "https://www.oliveyoung.co.kr/store/display/getCategoryShop.do?dispCatNo=100000100010009", "뷰티 디바이스"],
    ],
  },
  {
    site: "지그재그",
    urls: [
      ["FASHION", "https://zigzag.kr/catalog/products/110621280", "기존 상품 상세 성공 샘플"],
      ["FASHION", "https://zigzag.kr/catalog/products/166035170", "팬츠"],
      ["FASHION", "https://zigzag.kr/search?keyword=%EC%9B%90%ED%94%BC%EC%8A%A4", "검색: 원피스"],
      ["FASHION", "https://zigzag.kr/search?keyword=%EC%85%94%EC%B8%A0", "검색: 셔츠"],
      ["FASHION", "https://zigzag.kr/search?keyword=%ED%8C%AC%EC%B8%A0", "검색: 팬츠"],
      ["FASHION", "https://zigzag.kr/search?keyword=%EA%B0%80%EB%B0%A9", "검색: 가방"],
      ["FASHION", "https://zigzag.kr/search?keyword=%EC%8B%A0%EB%B0%9C", "검색: 신발"],
      ["BEAUTY", "https://zigzag.kr/search?keyword=%EB%A6%BD", "검색: 립"],
      ["BEAUTY", "https://zigzag.kr/search?keyword=%ED%81%AC%EB%A6%BC", "검색: 크림"],
      ["ETC", "https://zigzag.kr/search?keyword=%EC%9E%A1%ED%99%94", "검색: 잡화"],
    ],
  },
  {
    site: "에이블리",
    urls: [
      ["FASHION", "https://m.a-bly.com/goods/6070447", "기존 상품 상세 성공 샘플"],
      ["FASHION", "https://m.a-bly.com/search?keyword=%EA%B0%80%EB%94%94%EA%B1%B4", "검색: 가디건"],
      ["FASHION", "https://m.a-bly.com/search?keyword=%EC%9B%90%ED%94%BC%EC%8A%A4", "검색: 원피스"],
      ["FASHION", "https://m.a-bly.com/search?keyword=%EC%85%94%EC%B8%A0", "검색: 셔츠"],
      ["FASHION", "https://m.a-bly.com/search?keyword=%ED%8C%AC%EC%B8%A0", "검색: 팬츠"],
      ["FASHION", "https://m.a-bly.com/search?keyword=%EC%8A%A4%EB%8B%88%EC%BB%A4%EC%A6%88", "검색: 스니커즈"],
      ["FASHION", "https://m.a-bly.com/search?keyword=%EA%B0%80%EB%B0%A9", "검색: 가방"],
      ["BEAUTY", "https://m.a-bly.com/search?keyword=%EB%A6%BD", "검색: 립"],
      ["BEAUTY", "https://m.a-bly.com/search?keyword=%EC%BF%A0%EC%85%98", "검색: 쿠션"],
      ["ETC", "https://m.a-bly.com/search?keyword=%EB%AC%B8%EA%B5%AC", "검색: 문구"],
    ],
  },
  {
    site: "당근",
    urls: [
      ["LIVING", "https://www.daangn.com/kr/buy-sell/%EC%98%A4%EB%A5%B4%ED%85%8C-%EC%BB%A4%ED%94%BC%EB%A8%B8%EC%8B%A0-ock-352b-r5fvp77gm767/", "기존 검증 샘플"],
      ["DIGITAL", "https://www.daangn.com/kr/buy-sell/?search=%ED%82%A4%EB%B3%B4%EB%93%9C", "검색: 키보드"],
      ["DIGITAL", "https://www.daangn.com/kr/buy-sell/?search=%EB%A7%88%EC%9A%B0%EC%8A%A4", "검색: 마우스"],
      ["DIGITAL", "https://www.daangn.com/kr/buy-sell/?search=%EB%85%B8%ED%8A%B8%EB%B6%81", "검색: 노트북"],
      ["FASHION", "https://www.daangn.com/kr/buy-sell/?search=%EA%B0%80%EB%B0%A9", "검색: 가방"],
      ["LIVING", "https://www.daangn.com/kr/buy-sell/?search=%ED%85%8C%EC%9D%B4%EB%B8%94", "검색: 테이블"],
      ["HOBBY", "https://www.daangn.com/kr/buy-sell/?search=%EC%BA%A0%ED%95%91", "검색: 캠핑"],
      ["HOBBY", "https://www.daangn.com/kr/buy-sell/?search=%EC%9E%90%EC%A0%84%EA%B1%B0", "검색: 자전거"],
      ["FOOD", "https://www.daangn.com/kr/buy-sell/?search=%EC%BB%A4%ED%94%BC", "검색: 커피"],
      ["SUBSCRIPTION", "https://www.daangn.com/kr/buy-sell/?search=%EC%A0%95%EA%B8%B0%EA%B5%AC%EB%8F%85", "검색: 정기구독"],
    ],
  },
  {
    site: "번개장터",
    urls: [
      ["DIGITAL", "https://globalbunjang.com/product/362866868/", "기존 아이폰 케이스 샘플"],
      ["DIGITAL", "https://globalbunjang.com/search?q=%ED%82%A4%EB%B3%B4%EB%93%9C", "검색: 키보드"],
      ["DIGITAL", "https://globalbunjang.com/search?q=%EC%95%84%EC%9D%B4%ED%8F%B0", "검색: 아이폰"],
      ["FASHION", "https://globalbunjang.com/search?q=%EC%85%94%EC%B8%A0", "검색: 셔츠"],
      ["FASHION", "https://globalbunjang.com/search?q=%EA%B0%80%EB%B0%A9", "검색: 가방"],
      ["LIVING", "https://globalbunjang.com/search?q=%EC%BB%A4%ED%94%BC%EB%A8%B8%EC%8B%A0", "검색: 커피머신"],
      ["HOBBY", "https://globalbunjang.com/search?q=%ED%94%BC%EA%B7%9C%EC%96%B4", "검색: 피규어"],
      ["HOBBY", "https://globalbunjang.com/search?q=%EC%9E%90%EC%A0%84%EA%B1%B0", "검색: 자전거"],
      ["FOOD", "https://globalbunjang.com/search?q=%ED%94%84%EB%A1%9C%ED%8B%B4", "검색: 프로틴"],
      ["SUBSCRIPTION", "https://globalbunjang.com/search?q=%EB%A9%A4%EB%B2%84%EC%8B%AD", "검색: 멤버십"],
    ],
  },
];

function cleanText(value) {
  return value?.replace(/\s+/g, " ").trim() || null;
}

function isBlockingText(value) {
  return /잠시만 기다리십시오|just a moment|access denied|forbidden|captcha|robot|bot check/i.test(value ?? "");
}

function sourceDomain(url) {
  try {
    return new URL(url).hostname;
  } catch {
    return "";
  }
}

function containsAny(haystack, ...keywords) {
  return keywords.some((keyword) => haystack.includes(keyword));
}

function urlText(url) {
  try {
    const parsed = new URL(url);
    const queryText = [...parsed.searchParams.values()].join(" ");
    return decodeURIComponent(`${parsed.pathname} ${queryText}`)
      .replace(/[-_/=&?]+/g, " ")
      .toLowerCase();
  } catch {
    return "";
  }
}

function classifyOriginal(sourceDomainValue, title, summary) {
  const haystack = `${sourceDomainValue ?? ""} ${title ?? ""} ${summary ?? ""}`.toLowerCase();
  if (containsAny(haystack, "셔츠", "니트", "팬츠", "아우터", "원피스", "스니커즈", "신발", "가방", "musinsa", "zigzag", "ably", "29cm")) return "FASHION";
  if (containsAny(haystack, "립", "쿠션", "에센스", "크림", "마스크팩", "oliveyoung", "향수", "샴푸")) return "BEAUTY";
  if (containsAny(haystack, "이어폰", "헤드폰", "키보드", "마우스", "노트북", "갤럭시", "아이폰", "ipad", "monitor", "ssd")) return "DIGITAL";
  if (containsAny(haystack, "컵", "머그", "침구", "수납", "조명", "청소", "커피머신", "테이블")) return "LIVING";
  if (containsAny(haystack, "간식", "음료", "커피", "프로틴", "식품", "라면", "과자")) return "FOOD";
  if (containsAny(haystack, "레고", "피규어", "게임", "취미", "캠핑", "자전거")) return "HOBBY";
  if (containsAny(haystack, "subscription", "멤버십", "정기구독", "월간", "연간 구독")) return "SUBSCRIPTION";
  return "ETC";
}

function classifyStrict(title, summary) {
  const contentText = `${title ?? ""} ${summary ?? ""}`.toLowerCase();
  return classifyKeywordText(contentText);
}

function classifyMaxCrawl(title, summary, url, finalUrl) {
  const contentText = `${title ?? ""} ${summary ?? ""} ${urlText(url)} ${urlText(finalUrl)}`.toLowerCase();
  return classifyKeywordText(contentText);
}

function classifyKeywordText(contentText) {
  if (containsAny(contentText, "립", "틴트", "쿠션", "파운데이션", "에센스", "세럼", "앰플", "크림", "로션", "토너", "스킨", "마스크팩", "패드", "선크림", "클렌징", "향수", "샴푸", "바디워시", "바디오일", "스크럽", "네일")) return "BEAUTY";
  if (containsAny(contentText, "이어폰", "헤드폰", "키보드", "마우스", "노트북", "갤럭시", "아이폰", "아이패드", "ipad", "monitor", "모니터", "ssd", "케이스", "충전기", "케이블", "태블릿", "스마트워치", "보조배터리", "디바이스")) return "DIGITAL";
  if (containsAny(contentText, "컵", "머그", "이불", "홑이불", "침구", "베개", "러그", "매트", "수납", "조명", "청소", "커피머신", "테이블", "도자기", "접시", "그릇", "주방", "욕실", "디퓨저", "방향제")) return "LIVING";
  if (containsAny(contentText, "간식", "음료", "커피", "프로틴", "식품", "라면", "과자", "쉐이크", "단백질", "초콜릿", "젤리")) return "FOOD";
  if (containsAny(contentText, "레고", "피규어", "게임", "취미", "캠핑", "자전거", "보드게임", "퍼즐", "낚시", "등산")) return "HOBBY";
  if (containsAny(contentText, "subscription", "멤버십", "정기구독", "월간", "연간 구독", "구독권", "이용권", "기프트카드")) return "SUBSCRIPTION";
  if (containsAny(contentText, "셔츠", "티셔츠", "니트", "knit", "가디건", "팬츠", "데님", "아우터", "자켓", "재킷", "점퍼", "원피스", "스커트", "스니커즈", "신발", "구두", "샌들", "부츠", "가방", "백팩", "숄더백", "bag", "모자", "볼캡", "벨트", "지갑", "양말", "트위드", "부티크", "boutique", "setup", "mini setup")) return "FASHION";
  return "ETC";
}

function extractMeta(html, name) {
  const patterns = [
    new RegExp(`<meta[^>]+property=["']${name}["'][^>]+content=["']([^"']+)["']`, "i"),
    new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]+property=["']${name}["']`, "i"),
    new RegExp(`<meta[^>]+name=["']${name}["'][^>]+content=["']([^"']+)["']`, "i"),
    new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]+name=["']${name}["']`, "i"),
  ];
  for (const pattern of patterns) {
    const match = html.match(pattern);
    if (match?.[1]) return cleanText(match[1]);
  }
  return null;
}

async function crawlOne(context, site, expected, url, note) {
  const page = await context.newPage();
  try {
    let response = null;
    let html = "";
    let bodyText = "";
    let title = "";
    let summary = "";

    for (let attempt = 1; attempt <= 2; attempt += 1) {
      response = await page.goto(url, { waitUntil: "domcontentloaded", timeout: 20_000 });
      await page.waitForTimeout(BROWSER_WAIT_MS);
      await page.waitForLoadState("networkidle", { timeout: 2_000 }).catch(() => {});

      html = await page.content();
      bodyText = cleanText(await page.locator("body").innerText({ timeout: 5_000 }).catch(() => ""));
      title = cleanText(extractMeta(html, "og:title") ?? await page.title());
      summary = cleanText(extractMeta(html, "og:description") ?? extractMeta(html, "description") ?? bodyText?.slice(0, 5000));

      if (!isBlockingText(`${title} ${summary}`) || attempt === 2) {
        break;
      }
      await page.reload({ waitUntil: "domcontentloaded", timeout: 20_000 }).catch(() => {});
    }

    const finalUrl = page.url();
    const originalCategory = classifyOriginal(sourceDomain(finalUrl), title, summary);
    const strictCategory = classifyStrict(title, summary);
    const maxCrawlCategory = classifyMaxCrawl(title, summary, url, finalUrl);
    const status = response?.status() ?? null;
    const extractedContext = cleanText(`${title ?? ""} ${summary ?? ""} ${urlText(url)} ${urlText(finalUrl)}`);
    const browserOpened = Boolean(status && status < 400 && extractedContext);
    return {
      site,
      expected,
      originalCategory,
      strictCategory,
      maxCrawlCategory,
      originalMatched: expected === originalCategory,
      strictMatched: expected === strictCategory,
      maxCrawlMatched: expected === maxCrawlCategory,
      crawlOk: browserOpened,
      httpStatus: status,
      browserWaitMs: BROWSER_WAIT_MS,
      url,
      finalUrl,
      title,
      summary,
      note,
    };
  } catch (error) {
    return {
      site,
      expected,
      originalCategory: null,
      strictCategory: null,
      maxCrawlCategory: null,
      originalMatched: false,
      strictMatched: false,
      maxCrawlMatched: false,
      crawlOk: false,
      httpStatus: null,
      url,
      finalUrl: page.url(),
      title: null,
      note,
      error: error instanceof Error ? error.message : String(error),
    };
  } finally {
    await page.close().catch(() => {});
  }
}

function markdown(results) {
  const bySite = Map.groupBy(results, (result) => result.site);
  const total = results.length;
  const crawled = results.filter((result) => result.crawlOk).length;
  const originalMatched = results.filter((result) => result.crawlOk && result.originalMatched).length;
  const strictMatched = results.filter((result) => result.crawlOk && result.strictMatched).length;
  const maxCrawlMatched = results.filter((result) => result.crawlOk && result.maxCrawlMatched).length;
  const categoryRows = Object.entries(Object.groupBy(results, (result) => result.expected))
    .map(([category, items]) => `| ${category} | ${items.length} | ${items.filter((item) => item.crawlOk).length} | ${items.filter((item) => item.crawlOk && item.originalMatched).length} | ${items.filter((item) => item.crawlOk && item.strictMatched).length} | ${items.filter((item) => item.crawlOk && item.maxCrawlMatched).length} |`);

  const lines = [
    "# 카테고리 자동분류 사이트별 10개 크롤링 테스트",
    "",
    `실행일: ${new Date().toISOString()}`,
    "",
    "## 요약",
    "",
    `- 기준: 쿠팡 제외, 기존 크롤링 검증 후보 사이트별 10개씩`,
    `- 카테고리 기준: 현재 백엔드 enum 8종`,
    `- 비교 방식: original=현재 코드 그대로, strict=title/summary 보강 키워드, maxCrawl=title/summary/request URL 검색어 보강 키워드`,
    `- 총 샘플: ${total}개`,
    `- 크롤 성공: ${crawled}/${total}`,
    `- original 정답: ${originalMatched}/${crawled}`,
    `- strict 정답: ${strictMatched}/${crawled}`,
    `- maxCrawl 정답: ${maxCrawlMatched}/${crawled}`,
    "",
    "## 사이트별 요약",
    "",
    "| 사이트 | 샘플 | 크롤 성공 | original 정답 | strict 정답 | maxCrawl 정답 |",
    "|---|---:|---:|---:|---:|---:|",
    ...Array.from(bySite.entries()).map(([site, items]) => `| ${site} | ${items.length} | ${items.filter((item) => item.crawlOk).length} | ${items.filter((item) => item.crawlOk && item.originalMatched).length} | ${items.filter((item) => item.crawlOk && item.strictMatched).length} | ${items.filter((item) => item.crawlOk && item.maxCrawlMatched).length} |`),
    "",
    "## 카테고리별 요약",
    "",
    "| 기대 카테고리 | 샘플 | 크롤 성공 | original 정답 | strict 정답 | maxCrawl 정답 |",
    "|---|---:|---:|---:|---:|---:|",
    ...categoryRows,
    "",
    "## 상세 결과",
    "",
    "| 번호 | 사이트 | 기대 | original | strict | maxCrawl | 크롤 | 판정 | 제목/오류 | 비고 | URL |",
    "|---:|---|---|---|---|---|---|---|---|---|---|",
    ...results.map((result, index) => {
      const title = (result.title ?? result.error ?? "-").replaceAll("|", "/");
      const verdict = !result.crawlOk ? "크롤 실패" : result.maxCrawlMatched ? (result.originalMatched ? "기존도 정답" : "max 개선") : (result.originalMatched ? "max 악화" : "둘 다 오분류");
      return `| ${index + 1} | ${result.site} | ${result.expected} | ${result.originalCategory ?? "-"} | ${result.strictCategory ?? "-"} | ${result.maxCrawlCategory ?? "-"} | ${result.crawlOk ? "성공" : "실패"} | ${verdict} | ${title} | ${result.note} | ${result.url} |`;
    }),
  ];
  return `${lines.join("\n")}\n`;
}

await fs.mkdir(OUTPUT_DIR, { recursive: true });

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ ...devices["iPhone 13"], locale: "ko-KR" });
const jobs = SITES.flatMap((site) => site.urls.map(([expected, url, note]) => ({
  site: site.site,
  expected,
  url,
  note,
})));
const results = [];

try {
  const concurrency = Number(process.env.BROWSER_CONCURRENCY ?? 8);
  let cursor = 0;
  async function worker() {
    while (cursor < jobs.length) {
      const job = jobs[cursor];
      cursor += 1;
      results.push(await crawlOne(context, job.site, job.expected, job.url, job.note));
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker));
} finally {
  await context.close().catch(() => {});
  await browser.close().catch(() => {});
}

await fs.writeFile(OUTPUT_JSON, JSON.stringify(results, null, 2));
await fs.writeFile(OUTPUT_MD, markdown(results));
console.log(markdown(results));
