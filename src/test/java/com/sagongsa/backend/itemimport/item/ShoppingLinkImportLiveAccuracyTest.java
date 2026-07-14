package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SHOPPING_IMPORT_LIVE_TEST", matches = "true")
class ShoppingLinkImportLiveAccuracyTest {

	private static final String USER_AGENT =
		"Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 Chrome/135.0.0.0 Mobile Safari/537.36";
	private static final Pattern MUSINSA_TITLE = Pattern.compile("\\\"goodsNm\\\":\\\"((?:\\\\.|[^\\\"])*)\\\"");
	private static final Pattern MUSINSA_FINAL_PRICE = Pattern.compile(
		"(?s)\\\"goodsPrice\\\"\\s*:\\s*\\{.{0,2000}?\\\"finalPrice\\\"\\s*:\\s*([0-9]+)"
	);
	private static final Pattern WON_PRICE = Pattern.compile("([0-9][0-9,]*)\\s*원");
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String VERIFIED_ZIGZAG_URL =
		"https://zigzag.kr/catalog/products/141911042?catalog_product_id=141911042";
	private static final List<String> NAVER_PRODUCT_URLS = List.of(
		"https://brand.naver.com/cookierun/products/13637160702",
		"https://brand.naver.com/cookierun/products/12990367709",
		"https://brand.naver.com/cookierun/products/13105015634",
		"https://brand.naver.com/cookierun/products/13642188357",
		"https://brand.naver.com/cookierun/products/13642178853",
		"https://brand.naver.com/cookierun/products/12814389418",
		"https://brand.naver.com/cookierun/products/13642219327",
		"https://brand.naver.com/cookierun/products/13642196492",
		"https://brand.naver.com/cookierun/products/13654507764",
		"https://brand.naver.com/cookierun/products/13111595590",
		"https://brand.naver.com/cookierun/products/13111588657",
		"https://brand.naver.com/cookierun/products/13312546771",
		"https://brand.naver.com/cookierun/products/13312547912",
		"https://brand.naver.com/cookierun/products/13304682325",
		"https://brand.naver.com/cookierun/products/13304629944"
	);
	private static final List<String> OLIVE_YOUNG_PRODUCT_URLS = List.of(
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000171427",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000149780",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000110058",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000191160",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000244060",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=B000000226396",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000137964",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000229092",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000002345",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000230109",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000145662",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000137482",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000228412",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000189171",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000122760",
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000219553"
	);
	private static final List<String> SMART_STORE_PRODUCT_URLS = List.of(
		"https://smartstore.naver.com/jejuokay/products/11933395125",
		"https://smartstore.naver.com/aint/products/11891627059",
		"https://smartstore.naver.com/seragio/products/294040523",
		"https://smartstore.naver.com/emart_delivery/products/8131877184",
		"https://smartstore.naver.com/kongstiker/products/7326061721",
		"https://smartstore.naver.com/cooksaeng/products/11240228812",
		"https://smartstore.naver.com/ksat_ipsi/products/10131925214",
		"https://smartstore.naver.com/ksat_ipsi/products/8167665940",
		"https://smartstore.naver.com/ksat_ipsi/products/8167672040",
		"https://smartstore.naver.com/meaningfulstone/products/5159879795",
		"https://smartstore.naver.com/main/products/7144890306"
	);
	private static final List<String> KREAM_PRODUCT_URLS = List.of(
		"https://kream.co.kr/products/978283",
		"https://kream.co.kr/products/893073",
		"https://kream.co.kr/products/984764",
		"https://kream.co.kr/products/983060",
		"https://kream.co.kr/products/978285",
		"https://kream.co.kr/products/984835",
		"https://kream.co.kr/products/852681",
		"https://kream.co.kr/products/941062",
		"https://kream.co.kr/products/985705",
		"https://kream.co.kr/products/983745",
		"https://kream.co.kr/products/985846",
		"https://kream.co.kr/products/984825",
		"https://kream.co.kr/products/852706",
		"https://kream.co.kr/products/984763",
		"https://kream.co.kr/products/984544"
	);
	private static final List<String> TWENTY_NINE_CM_PRODUCT_URLS = List.of(
		"https://www.29cm.co.kr/products/3853210",
		"https://www.29cm.co.kr/products/3307910",
		"https://www.29cm.co.kr/products/3053978",
		"https://product.29cm.co.kr/catalog/2602166",
		"https://product.29cm.co.kr/catalog/3053461",
		"https://product.29cm.co.kr/catalog/1584496",
		"https://product.29cm.co.kr/catalog/364149?id=3264999",
		"https://product.29cm.co.kr/catalog/2860848?id=954390",
		"https://product.29cm.co.kr/catalog/2746240",
		"https://product.29cm.co.kr/catalog/3918491",
		"https://product.29cm.co.kr/catalog/3486317",
		"https://www.29cm.co.kr/products/2070013",
		"https://www.29cm.co.kr/products/3960939",
		"https://www.29cm.co.kr/products/2525794",
		"https://www.29cm.co.kr/products/4042000",
		"https://www.29cm.co.kr/products/3304280",
		"https://www.29cm.co.kr/products/2530414"
	);
	private static final List<String> ABLY_PRODUCT_URLS = List.of(
		"https://m.a-bly.com/goods/70247267",
		"https://m.a-bly.com/goods/62561082",
		"https://m.a-bly.com/goods/70219189",
		"https://m.a-bly.com/goods/6070447",
		"https://m.a-bly.com/goods/10968940",
		"https://m.a-bly.com/goods/68993180",
		"https://m.a-bly.com/goods/56497725",
		"https://m.a-bly.com/goods/19346326",
		"https://m.a-bly.com/goods/12235563",
		"https://m.a-bly.com/goods/7111823",
		"https://m.a-bly.com/goods/40829813",
		"https://m.a-bly.com/goods/46925893",
		"https://m.a-bly.com/goods/13605337",
		"https://m.a-bly.com/goods/36174732",
		"https://m.a-bly.com/goods/12426697"
	);
	private static final List<String> REPORTED_ZIGZAG_STORE_URLS = List.of(
		"https://store.zigzag.kr/catalog/products/136095576?catalog_product_id=136095576",
		"https://store.zigzag.kr/catalog/products/160269610?catalog_product_id=160269610",
		"https://store.zigzag.kr/catalog/products/140798161?catalog_product_id=140798161"
	);
	private static final List<String> REPORTED_ABLY_SHARE_URLS = List.of(
		"https://ably.airbridge.io/goods/70247267",
		"https://ably.airbridge.io/goods/62561082",
		"https://ably.airbridge.io/goods/70219189"
	);
	private static final List<String> REPORTED_TWENTY_NINE_CM_URLS = List.of(
		"https://www.29cm.co.kr/products/3853210",
		"https://www.29cm.co.kr/products/3307910",
		"https://www.29cm.co.kr/products/3053978"
	);
	private static final String REPORTED_OLIVE_YOUNG_URL =
		"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000171427";
	private static final String REPORTED_DAANGN_JOB_URL = "https://www.daangn.com/kr/jobs/oc89uuf1a4zo";
	private static final String REPORTED_MUSINSA_URL = "https://www.musinsa.com/products/1855218";

	@Test
	void verifiesFiftyKoreanProductsAgainstSourceMetadata() throws Exception {
		List<String> mismatches = new ArrayList<>();
		List<String> unavailable = new ArrayList<>();
		int musinsa = verifyCandidates(discoverMusinsaProducts(), 18, mismatches, unavailable);
		int daangn = verifyCandidates(discoverDaangnProducts(), 17, mismatches, unavailable);
		int bunjang = verifyCandidates(discoverBunjangProducts(), 15, mismatches, unavailable);

		assertThat(mismatches).as("title, KRW price, and product image must exactly match the source oracle").isEmpty();
		assertThat(musinsa).as("verified Musinsa products; unavailable=%s", unavailable).isEqualTo(18);
		assertThat(daangn).as("verified Daangn products; unavailable=%s", unavailable).isEqualTo(17);
		assertThat(bunjang).as("verified Bunjang products; unavailable=%s", unavailable).isEqualTo(15);
		assertThat(musinsa + daangn + bunjang).isEqualTo(50);
	}

	@Test
	void verifiesTenApprovedZigzagProductsAgainstSourceMetadata() throws IOException {
		List<String> mismatches = new ArrayList<>();
		List<String> unavailable = new ArrayList<>();

		int verified = verifyCandidates(discoverZigzagProducts(), 10, mismatches, unavailable);

		assertThat(mismatches).as("Zigzag title, KRW price, and product image must match").isEmpty();
		assertThat(verified).as("verified Zigzag products; unavailable=%s", unavailable).isEqualTo(10);
	}

	@Test
	void verifiesTenApprovedNaverShoppingAndOliveYoungProductsAgainstSourceMetadata() {
		List<String> mismatches = new ArrayList<>();
		List<String> unavailable = new ArrayList<>();
		ShoppingImportProperties properties = new ShoppingImportProperties();
		properties.getBrowserFetch().setRenderWait(java.time.Duration.ofSeconds(2));

		int naver;
		int oliveYoung;
		try (BrowserPageFetcher browser = new BrowserPageFetcher(properties.getBrowserFetch(), 10_000_000)) {
			naver = verifyCandidates(NAVER_PRODUCT_URLS, 10, mismatches, unavailable, browser);
			oliveYoung = verifyCandidates(OLIVE_YOUNG_PRODUCT_URLS, 10, mismatches, unavailable, browser);
		}

		assertThat(mismatches).as("Naver Shopping and Olive Young title, KRW price, and product image must match").isEmpty();
		assertThat(naver).as("verified Naver Shopping products; unavailable=%s", unavailable).isEqualTo(10);
		assertThat(oliveYoung).as("verified Olive Young products; unavailable=%s", unavailable).isEqualTo(10);
	}

	@Test
	void verifiesFiveApprovedKreamProductsInBatchOneAgainstSourceMetadata() {
		verifyKreamBatch(KREAM_PRODUCT_URLS.subList(0, 5));
	}

	@Test
	void verifiesFiveApprovedKreamProductsInBatchTwoAgainstSourceMetadata() {
		verifyKreamBatch(KREAM_PRODUCT_URLS.subList(5, 10));
	}

	private void verifyKreamBatch(List<String> candidates) {
		List<String> mismatches = new ArrayList<>();
		List<String> unavailable = new ArrayList<>();
		int kream = verifyCandidates(candidates, 5, mismatches, unavailable, new JsoupPageFetcher(10_000_000));

		assertThat(mismatches).as("KREAM title, KRW price, and product image must match").isEmpty();
		assertThat(kream).as("verified KREAM products; unavailable=%s", unavailable).isEqualTo(5);
	}

	@Test
	void verifiesTenApprovedSmartStoreProductsAgainstSourceMetadata() {
		List<String> mismatches = new ArrayList<>();
		List<String> unavailable = new ArrayList<>();
		ShoppingImportProperties properties = browserProperties();

		int smartStore;
		try (BrowserPageFetcher browser = new BrowserPageFetcher(properties.getBrowserFetch(), 10_000_000)) {
			smartStore = verifyCandidates(SMART_STORE_PRODUCT_URLS, 10, mismatches, unavailable, browser);
		}

		assertThat(mismatches).as("Smart Store title, KRW price, and product image must match").isEmpty();
		assertThat(smartStore).as("verified Smart Store products; unavailable=%s", unavailable).isEqualTo(10);
	}

	@Test
	void verifiesTenApprovedTwentyNineCmProductsAgainstSourceMetadata() {
		List<String> mismatches = new ArrayList<>();
		List<String> unavailable = new ArrayList<>();

		int verified = verifyCandidates(
			TWENTY_NINE_CM_PRODUCT_URLS,
			10,
			mismatches,
			unavailable,
			new JsoupPageFetcher(10_000_000)
		);

		assertThat(mismatches).as("29CM title, KRW price, and product image must match").isEmpty();
		assertThat(verified).as("verified 29CM products; unavailable=%s", unavailable).isEqualTo(10);
	}

	@Test
	void verifiesTenApprovedAblyProductsAgainstSourceMetadata() {
		List<String> mismatches = new ArrayList<>();
		List<String> unavailable = new ArrayList<>();
		ShoppingImportProperties properties = browserProperties();

		int verified;
		try (BrowserPageFetcher browser = new BrowserPageFetcher(properties.getBrowserFetch(), 20_000_000)) {
			verified = verifyCandidates(ABLY_PRODUCT_URLS, 10, mismatches, unavailable, browser);
		}

		assertThat(mismatches).as("Ably title, KRW price, and product image must match").isEmpty();
		assertThat(verified).as("verified Ably products; unavailable=%s", unavailable).isEqualTo(10);
	}

	@Test
	void verifiesReportedShareLinkRegressionsAgainstSourceMetadata() {
		List<String> mismatches = new ArrayList<>();
		List<String> unavailable = new ArrayList<>();
		ShoppingImportProperties properties = browserProperties();

		int zigzag = verifyCandidates(REPORTED_ZIGZAG_STORE_URLS, 3, mismatches, unavailable);
		int twentyNineCm = verifyCandidates(REPORTED_TWENTY_NINE_CM_URLS, 3, mismatches, unavailable);
		int musinsa = verifyCandidates(List.of(REPORTED_MUSINSA_URL), 1, mismatches, unavailable);
		int ably;
		int oliveYoung;
		try (BrowserPageFetcher browser = new BrowserPageFetcher(properties.getBrowserFetch(), 20_000_000)) {
			ably = verifyCandidates(REPORTED_ABLY_SHARE_URLS, 3, mismatches, unavailable, browser);
			oliveYoung = verifyCandidates(List.of(REPORTED_OLIVE_YOUNG_URL), 1, mismatches, unavailable, browser);
		}
		ShoppingLinkImportService service = new ShoppingLinkImportService(new JsoupPageFetcher(10_000_000), OBJECT_MAPPER);

		assertThat(mismatches).as("reported share-link title, KRW price, and product image must match").isEmpty();
		assertThat(zigzag).as("reported Zigzag products; unavailable=%s", unavailable).isEqualTo(3);
		assertThat(twentyNineCm).as("reported 29CM products; unavailable=%s", unavailable).isEqualTo(3);
		assertThat(musinsa).as("reported Musinsa product; unavailable=%s", unavailable).isEqualTo(1);
		assertThat(ably).as("reported Ably products; unavailable=%s", unavailable).isEqualTo(3);
		assertThat(oliveYoung).as("reported Olive Young product; unavailable=%s", unavailable).isEqualTo(1);
		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(ItemInputSource.SHARE, REPORTED_DAANGN_JOB_URL, null, null, null, null)
		)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
			.hasMessageContaining("Shopping product redirected outside its verified domain");
	}

	private ShoppingImportProperties browserProperties() {
		ShoppingImportProperties properties = new ShoppingImportProperties();
		properties.getBrowserFetch().setRenderWait(java.time.Duration.ofSeconds(2));
		return properties;
	}

	private int verifyCandidates(
		List<String> candidates,
		int required,
		List<String> mismatches,
		List<String> unavailable
	) {
		return verifyCandidates(candidates, required, mismatches, unavailable, new JsoupPageFetcher());
	}

	private int verifyCandidates(
		List<String> candidates,
		int required,
		List<String> mismatches,
		List<String> unavailable,
		PageFetcher delegate
	) {
		int verified = 0;
		for (String url : candidates) {
			if (verified >= required) {
				break;
			}
			try {
				RecordingPageFetcher fetcher = new RecordingPageFetcher(delegate);
				ShoppingLinkImportService service = new ShoppingLinkImportService(fetcher, OBJECT_MAPPER);
				ShoppingLinkImportResponse response = service.importLink(
					new ShoppingLinkImportRequest(ItemInputSource.SHARE, url, null, null, null, null)
				);
				SourceProduct expected = sourceProduct(fetcher.lastPage());
				SavedItemDraft actual = response.item();
				List<String> fields = new ArrayList<>();
				if (!normalizeText(expected.title()).equals(normalizeText(actual.title()))) fields.add("title");
				if (!expected.price().equals(actual.listedPrice())) fields.add("listedPrice");
				if (!"KRW".equals(actual.currencyCode())) fields.add("currencyCode");
				if (!expected.imageUrl().equals(actual.imageUrl())) fields.add("imageUrl");
				if (fields.isEmpty()) {
					verified++;
				} else {
					mismatches.add(url + " fields=" + fields + " expected=" + expected + " actual=" + actual);
				}
			} catch (Exception exception) {
				unavailable.add(url + " -> " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
			}
		}
		return verified;
	}

	private List<String> discoverMusinsaProducts() throws IOException {
		Document document = Jsoup.connect("https://www.musinsa.com/search/goods?keyword=%EC%85%94%EC%B8%A0")
			.userAgent(USER_AGENT)
			.timeout(30_000)
			.get();
		Set<String> urls = new LinkedHashSet<>();
		Pattern pattern = Pattern.compile("https://www\\.musinsa\\.com/products/[0-9]+");
		Matcher matcher = pattern.matcher(document.html());
		while (matcher.find()) urls.add(matcher.group());
		return List.copyOf(urls);
	}

	private List<String> discoverDaangnProducts() throws IOException {
		Document document = Jsoup.connect("https://www.daangn.com/kr/buy-sell/?search=%EB%85%B8%ED%8A%B8%EB%B6%81")
			.userAgent(USER_AGENT)
			.timeout(30_000)
			.get();
		Set<String> urls = new LinkedHashSet<>();
		Pattern pattern = Pattern.compile("https://www\\.daangn\\.com/kr/buy-sell/[^\\\"? ]+/");
		Matcher matcher = pattern.matcher(document.html());
		while (matcher.find()) urls.add(matcher.group());
		return List.copyOf(urls);
	}

	private List<String> discoverBunjangProducts() throws Exception {
		String query = URLEncoder.encode("아이폰", StandardCharsets.UTF_8);
		URI uri = URI.create("https://api.bunjang.co.kr/api/1/find_v2.json?q=" + query
			+ "&order=date&page=0&n=40&stat_device=w&req_ref=search");
		HttpRequest request = HttpRequest.newBuilder(uri).header("User-Agent", USER_AGENT).GET().build();
		String body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
		JsonNode list = OBJECT_MAPPER.readTree(body).path("list");
		List<String> urls = new ArrayList<>();
		for (JsonNode product : list) {
			String productId = product.path("pid").asText();
			if (!productId.isBlank()) urls.add("https://m.bunjang.co.kr/products/" + productId);
		}
		return urls;
	}

	private List<String> discoverZigzagProducts() throws IOException {
		Document document = Jsoup.connect("https://zigzag.kr/ggsing/best/507")
			.userAgent(USER_AGENT)
			.timeout(30_000)
			.get();
		Set<String> urls = new LinkedHashSet<>();
		urls.add(VERIFIED_ZIGZAG_URL);
		Pattern pattern = Pattern.compile("/(?:app/)?catalog/products/[0-9]+");
		Matcher matcher = pattern.matcher(document.html());
		while (matcher.find()) {
			String publicPath = matcher.group().replaceFirst("^/app/", "/");
			urls.add("https://zigzag.kr" + publicPath);
		}
		return List.copyOf(urls);
	}

	private SourceProduct sourceProduct(FetchedPage page) throws Exception {
		String host = page.finalUri().getHost().toLowerCase(Locale.ROOT);
		Document document = Jsoup.parse(page.body(), page.finalUri().toString());
		if (host.contains("musinsa.com")) {
			return new SourceProduct(
				jsonString(MUSINSA_TITLE.matcher(page.body())),
				Integer.valueOf(regexGroup(MUSINSA_FINAL_PRICE.matcher(page.body()))),
				meta(document, "meta[property=og:image]")
			);
		}
		if (host.contains("daangn.com")) {
			JsonNode product = productJsonLd(document);
			return new SourceProduct(
				product.path("name").asText(),
				daangnDisplayedPrice(document, product),
				firstTextValue(product.path("image"))
			);
		}
		if (host.contains("bunjang.co.kr")) {
			return new SourceProduct(
				meta(document, "meta[property=og:title]"),
				Integer.valueOf(meta(document, "meta[property=product:price:amount]")),
				meta(document, "meta[property=og:image]")
			);
		}
		if (host.equals("zigzag.kr") || host.equals("www.zigzag.kr")) {
			JsonNode product = productJsonLd(document);
			return new SourceProduct(
				meta(document, "meta[property=og:title]"),
				Integer.valueOf(meta(document, "meta[property=product:price:amount]").replace(",", "")),
				Parser.unescapeEntities(firstTextValue(product.path("image")), false)
			);
		}
		if (host.endsWith("oliveyoung.co.kr")) {
			JsonNode product = optionalProductJsonLd(document);
			String title = product == null ? meta(document, "meta[property=og:title]") : product.path("name").asText();
			String image = product == null ? meta(document, "meta[property=og:image]") : firstTextValue(product.path("image"));
			return new SourceProduct(
				cleanOliveYoungTitle(title),
				Integer.valueOf(meta(document, "meta[property=eg:salePrice]").replace(",", "")),
				Parser.unescapeEntities(image, false)
			);
		}
		if (host.equals("brand.naver.com") || host.equals("m.brand.naver.com")
			|| host.equals("smartstore.naver.com") || host.equals("m.smartstore.naver.com")) {
			return new SourceProduct(
				meta(document, "meta[property=og:title]"),
				Integer.valueOf(meta(document, "meta[property=kakao:commerce:price]").replace(",", "")),
				meta(document, "meta[property=og:image]")
			);
		}
		if (host.equals("kream.co.kr") || host.equals("www.kream.co.kr")) {
			JsonNode product = optionalProductJsonLd(document);
			String title = product == null ? meta(document, "meta[property=og:title]") : product.path("name").asText();
			String price = product == null
				? meta(document, "meta[property=product:price:amount]")
				: product.path("offers").path("price").asText();
			String image = product == null ? meta(document, "meta[property=og:image]") : firstTextValue(product.path("image"));
			return new SourceProduct(
				title,
				new java.math.BigDecimal(price.replace(",", "")).intValueExact(),
				Parser.unescapeEntities(image, false)
			);
		}
		if (host.equals("www.29cm.co.kr") || host.equals("product.29cm.co.kr")) {
			JsonNode product = productJsonLd(document);
			return new SourceProduct(
				product.path("name").asText(),
				product.path("offers").path("price").decimalValue().intValueExact(),
				meta(document, "meta[property=og:image]")
			);
		}
		if (host.equals("m.a-bly.com")) {
			JsonNode product = productJsonLd(document);
			return new SourceProduct(
				product.path("name").asText(),
				product.path("offers").path("price").decimalValue().intValueExact(),
				firstTextValue(product.path("image"))
			);
		}
		throw new IllegalArgumentException("Unsupported oracle host: " + host);
	}

	private String cleanOliveYoungTitle(String title) {
		return normalizeText(title)
			.replaceFirst("\\s*/\\s*올리브영$", "")
			.replaceFirst("\\s*\\|\\s*올리브영$", "")
			.trim();
	}

	private JsonNode optionalProductJsonLd(Document document) throws IOException {
		for (Element script : document.select("script[type=application/ld+json]")) {
			JsonNode product = findProductNode(OBJECT_MAPPER.readTree(script.data()));
			if (product != null) return product;
		}
		return null;
	}

	private JsonNode productJsonLd(Document document) throws IOException {
		for (Element script : document.select("script[type=application/ld+json]")) {
			JsonNode product = findProductNode(OBJECT_MAPPER.readTree(script.data()));
			if (product != null) return product;
		}
		throw new IllegalArgumentException("Product JSON-LD not found");
	}

	private JsonNode findProductNode(JsonNode node) {
		if (node == null || node.isNull()) return null;
		if (node.isObject() && "Product".equalsIgnoreCase(node.path("@type").asText())) return node;
		Iterator<JsonNode> children = node.elements();
		while (children.hasNext()) {
			JsonNode product = findProductNode(children.next());
			if (product != null) return product;
		}
		return null;
	}

	private String firstTextValue(JsonNode node) {
		return node.isArray() ? node.path(0).asText() : node.asText();
	}

	private Integer daangnDisplayedPrice(Document document, JsonNode product) {
		String displayedPrice = meta(document, "meta[property=karrot:embed_view_type:title]");
		Matcher matcher = WON_PRICE.matcher(displayedPrice);
		if (matcher.find()) {
			return Integer.valueOf(matcher.group(1).replace(",", ""));
		}
		return product.path("offers").path("price").decimalValue().intValueExact();
	}

	private String normalizeText(String value) {
		return value == null ? null : value.replaceAll("\\s+", " ").trim();
	}

	private String meta(Document document, String selector) {
		Element element = document.selectFirst(selector);
		if (element == null || element.attr("content").isBlank()) {
			throw new IllegalArgumentException("Missing metadata: " + selector);
		}
		return element.attr("content");
	}

	private String jsonString(Matcher matcher) throws IOException {
		String escaped = regexGroup(matcher);
		return OBJECT_MAPPER.readValue("\"" + escaped + "\"", String.class);
	}

	private String regexGroup(Matcher matcher) {
		if (!matcher.find()) throw new IllegalArgumentException("Expected source field not found");
		return matcher.group(1);
	}

	private record SourceProduct(String title, Integer price, String imageUrl) {
	}

	private static final class RecordingPageFetcher implements PageFetcher {
		private final PageFetcher delegate;
		private FetchedPage lastPage;

		private RecordingPageFetcher(PageFetcher delegate) {
			this.delegate = delegate;
		}

		@Override
		public FetchedPage fetch(URI uri) {
			lastPage = delegate.fetch(uri);
			return lastPage;
		}

		private FetchedPage lastPage() {
			if (lastPage == null) throw new IllegalStateException("Page was not fetched");
			return lastPage;
		}
	}
}
