(function initExtractor(globalScope) {
  function normalizeWhitespace(value) {
    return value ? value.replace(/\s+/g, " ").trim() : null;
  }

  function normalizePrice(value) {
    if (!value) return null;
    const match = String(value).match(/\d{1,3}(?:,\d{3})*(?:\.\d+)?/);
    return match ? match[0] : null;
  }

  function isNoiseLine(line) {
    return /상품정보|서비스 네비게이션|상품 리스트 바로가기|카테고리 보기|검색|장바구니|옵션 열기\/닫기|쿠팡홈|마이쿠팡|로그인|PC버전|APP설치|상품설명|사용조건|매장위치|상품문의|기본정보|상세정보|공유|찜|품절|맨위로|지도보기|쿠팡가/.test(
      line,
    );
  }

  function absoluteUrl(url) {
    if (!url) return null;
    try {
      return new URL(url, window.location.href).toString();
    } catch {
      return null;
    }
  }

  function pickFirstText(selectors) {
    for (const selector of selectors) {
      const node = document.querySelector(selector);
      const text = normalizeWhitespace(node?.textContent || "");
      if (text) {
        return {
          selector,
          text
        };
      }
    }

    return null;
  }

  function pickFirstAttr(selectors, attr) {
    for (const selector of selectors) {
      const node = document.querySelector(selector);
      const value = normalizeWhitespace(node?.getAttribute(attr) || "");
      if (value) {
        return {
          selector,
          value
        };
      }
    }

    return null;
  }

  function extractJsonLdProducts() {
    const scripts = Array.from(
      document.querySelectorAll('script[type="application/ld+json"]')
    );
    const products = [];

    for (const script of scripts) {
      const text = script.textContent || "";
      if (!text.trim()) continue;

      try {
        const parsed = JSON.parse(text);
        const items = Array.isArray(parsed) ? parsed : [parsed];
        for (const item of items) {
          if (!item || typeof item !== "object") continue;
          if (item["@type"] === "Product") {
            products.push(item);
          }
          if (Array.isArray(item["@graph"])) {
            for (const nested of item["@graph"]) {
              if (nested?.["@type"] === "Product") {
                products.push(nested);
              }
            }
          }
        }
      } catch {
        // Ignore malformed JSON-LD and keep searching.
      }
    }

    return products;
  }

  function getBodyLines() {
    const bodyText = document.body?.innerText || "";
    return bodyText
      .split("\n")
      .map((line) => normalizeWhitespace(line))
      .filter(Boolean);
  }

  function findPriceInText(lines = getBodyLines()) {
    for (let index = 0; index < lines.length; index += 1) {
      const line = lines[index];

      if (
        /원/.test(line) &&
        !/적립|할인쿠폰|최대|캐시|배송비|무료배송|쿠폰/.test(line)
      ) {
        const price = normalizePrice(line);
        if (price) {
          return {
            source: "bodyText",
            lineIndex: index,
            raw: line,
            value: price,
          };
        }
      }
    }

    return null;
  }

  function findTitleInText(lines, priceInfo) {
    if (priceInfo?.lineIndex != null) {
      for (let index = priceInfo.lineIndex - 1; index >= 0; index -= 1) {
        const line = lines[index];
        if (!line || isNoiseLine(line)) continue;
        if (/\d/.test(line) && /원|%/.test(line)) continue;
        return {
          source: "bodyText",
          lineIndex: index,
          value: line,
        };
      }
    }

    for (const line of lines) {
      if (!line || isNoiseLine(line)) continue;
      if (/\d/.test(line) && /원|%/.test(line)) continue;
      if (line.length < 2) continue;
      return {
        source: "bodyText",
        value: line,
      };
    }

    return null;
  }

  function scoreImageCandidate(candidate) {
    let score = 0;
    const url = candidate.value || "";

    if (/vendorItemPackage|image\/product|thumb\/mhigh|thumb\/q/.test(url)) score += 50;
    if (/coupangcdn/.test(url)) score += 20;
    if (/icon|logo|mypromotion|displayitem/.test(url)) score -= 40;
    if (/sprite|badge|banner/.test(url)) score -= 30;
    score += Math.min(candidate.width || 0, 2000) / 40;
    score += Math.min(candidate.height || 0, 2000) / 40;
    if ((candidate.width || 0) < 120 || (candidate.height || 0) < 120) score -= 25;

    return score;
  }

  function extractImageCandidates() {
    const candidates = [];
    const selectors = [
      'meta[property="og:image"]',
      'img[alt*="상품"]',
      'img.prod-image__detail',
      'img[src*="image"]',
      'img[srcset]'
    ];

    for (const selector of selectors) {
      const nodes = Array.from(document.querySelectorAll(selector));
      for (const node of nodes) {
        const value =
          node.getAttribute?.("content") ||
          node.getAttribute?.("src") ||
          node.getAttribute?.("srcset") ||
          "";
        const normalized = absoluteUrl(value.split(",")[0].trim().split(" ")[0]);
        if (!normalized) continue;
        if (!/\.(jpg|jpeg|png|webp)/i.test(normalized)) continue;
        const rect = typeof node.getBoundingClientRect === "function"
          ? node.getBoundingClientRect()
          : { width: 0, height: 0 };
        const width =
          Number(node.naturalWidth) ||
          Number(node.width) ||
          Math.round(rect.width) ||
          0;
        const height =
          Number(node.naturalHeight) ||
          Number(node.height) ||
          Math.round(rect.height) ||
          0;

        candidates.push({
          selector,
          value: normalized,
          width,
          height,
        });
      }
    }

    return candidates
      .map((candidate) => ({
        ...candidate,
        score: scoreImageCandidate(candidate),
      }))
      .sort((left, right) => right.score - left.score);
  }

  function extractFromJsonLd() {
    const products = extractJsonLdProducts();
    for (const product of products) {
      const offers = Array.isArray(product.offers)
        ? product.offers[0]
        : product.offers;
      const price = normalizePrice(offers?.price || product?.price);
      const image = Array.isArray(product.image) ? product.image[0] : product.image;
      const title = normalizeWhitespace(product.name);

      if (price || image || title) {
        return {
          title,
          price,
          image: absoluteUrl(image),
          raw: product
        };
      }
    }

    return null;
  }

  function extractWithDomSelectors() {
    const title = pickFirstText([
      "h1",
      "[class*='prod-buy-header'] h1",
      "[class*='prod-buy-header__title']",
      "[data-testid='productName']"
    ]);

    const price = pickFirstText([
      "[class*='total-price']",
      "[class*='prod-sale-price']",
      "[class*='price'] strong",
      "[class*='price']"
    ]);

    const image = pickFirstAttr(
      [
        'meta[property="og:image"]',
        'img[class*="prod-image"]',
        'img[src*="coupangcdn"]',
        'img[src*="thumbnail"]'
      ],
      "content"
    ) || pickFirstAttr(
      [
        'img[class*="prod-image"]',
        'img[src*="coupangcdn"]',
        'img[src*="thumbnail"]'
      ],
      "src"
    );

    return {
      title: title?.text || null,
      titleSelector: title?.selector || null,
      price: normalizePrice(price?.text),
      priceSelector: price?.selector || null,
      image: absoluteUrl(image?.value),
      imageSelector: image?.selector || null
    };
  }

  function getPageKind() {
    if (/m\.coupang\.com/.test(location.hostname)) return "mobile";
    return "desktop";
  }

  function extractProductData() {
    const dom = extractWithDomSelectors();
    const jsonLd = extractFromJsonLd();
    const lines = getBodyLines();
    const bodyPrice = findPriceInText(lines);
    const bodyTitle = findTitleInText(lines, bodyPrice);
    const images = extractImageCandidates();
    const metaTitle = normalizeWhitespace(document.title);

    const result = {
      pageKind: getPageKind(),
      url: location.href,
      title: dom.title || jsonLd?.title || bodyTitle?.value || metaTitle,
      price: dom.price || jsonLd?.price || bodyPrice?.value || null,
      image:
        jsonLd?.image ||
        images[0]?.value ||
        dom.image ||
        null,
      evidence: {
        dom,
        jsonLd,
        bodyTitle,
        bodyPrice,
        imageCandidates: images.slice(0, 5)
      },
      extractedAt: new Date().toISOString()
    };

    result.success = Boolean(result.title || result.price || result.image);
    return result;
  }

  globalScope.CoupangExtractor = {
    extractProductData
  };
})(globalThis);
