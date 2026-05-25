package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.domain.enums.ItemStatus;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ShoppingLinkImportServiceTest {

	private FakePageFetcher pageFetcher;
	private ShoppingLinkImportService service;

	@BeforeEach
	void setUp() {
		pageFetcher = new FakePageFetcher();
		service = new ShoppingLinkImportService(pageFetcher, new ObjectMapper());
	}

	@Test
	void importsSharedLinkFromOpenGraphMetadata() {
		pageFetcher.stub(
			"https://shopping.example.com/products/100",
			"""
				<html>
				<head>
				  <title>Example</title>
				  <meta property="og:title" content="Air Runner Sneakers" />
				  <meta property="og:image" content="https://cdn.example.com/air-runner.jpg" />
				  <meta property="product:price:amount" content="129000" />
				</head>
				<body></body>
				</html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://shopping.example.com/products/100?utm_source=kakao",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().normalizedUrl()).isEqualTo("https://shopping.example.com/products/100");
		assertThat(response.sourceMetadata().sourceDomain()).isEqualTo("shopping.example.com");
		assertThat(response.item().title()).isEqualTo("Air Runner Sneakers");
		assertThat(response.item().listedPrice()).isEqualTo(129000);
		assertThat(response.item().imageUrl()).isEqualTo("https://cdn.example.com/air-runner.jpg");
		assertThat(response.sourceMetadata().extractionMethod()).isEqualTo("HTML_META");
		assertThat(response.item().status()).isEqualTo(ItemStatus.SAVED);
		assertThat(response.saveRequest().normalizedUrl()).isEqualTo("https://shopping.example.com/products/100");
		assertThat(response.saveRequest().sourceDomain()).isEqualTo("shopping.example.com");
		assertThat(response.warnings()).contains("추적성 query parameter를 제거했습니다.");
	}

	@Test
	void importsSharedLinkFromJsonLdMetadata() {
		pageFetcher.stub(
			"https://m.coupang.com/nm/products/3013825663",
			"""
				<html>
				<head>
				  <script type="application/ld+json">
				  {
				    "@context": "https://schema.org",
				    "@type": "Product",
				    "name": "쿠팡 무선 이어폰",
				    "image": ["https://image.coupangcdn.com/item.jpg"],
				    "brand": {
				      "@type": "Brand",
				      "name": "Coupang Basics"
				    },
				    "offers": {
				      "@type": "Offer",
				      "price": "49900"
				    }
				  }
				  </script>
				</head>
				<body></body>
				</html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://www.coupang.com/vp/products/3013825663?itemId=1",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().normalizedUrl()).isEqualTo("https://m.coupang.com/nm/products/3013825663");
		assertThat(response.item().title()).isEqualTo("쿠팡 무선 이어폰");
		assertThat(response.item().brandName()).isEqualTo("Coupang Basics");
		assertThat(response.item().listedPrice()).isEqualTo(49900);
		assertThat(response.sourceMetadata().extractionMethod()).isEqualTo("JSON_LD");
		assertThat(response.item().category()).isEqualTo(ItemCategory.DIGITAL);
		assertThat(response.warnings()).contains("쿠팡 링크를 모바일 상품 경로로 정규화했습니다.");
	}

	@Test
	void acceptsDirectInputWithoutRemoteFetch() {
		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.DIRECT_INPUT,
				"https://zigzag.kr/catalog/products/110621280",
				"여름 셔츠",
				"Zigzag",
				39000,
				"https://image.example.com/shirt.jpg"
			)
		);

		assertThat(response.item().title()).isEqualTo("여름 셔츠");
		assertThat(response.item().brandName()).isEqualTo("Zigzag");
		assertThat(response.item().listedPrice()).isEqualTo(39000);
		assertThat(response.sourceMetadata().extractionMethod()).isEqualTo("MANUAL");
		assertThat(response.sourceMetadata().sourceDomain()).isEqualTo("zigzag.kr");
		assertThat(response.item().category()).isEqualTo(ItemCategory.FASHION);
	}

	@Test
	void rejectsNegativeDirectInputPrice() {
		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.DIRECT_INPUT,
				null,
				"수동 입력 상품",
				null,
				-1,
				null
			)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> {
				ResponseStatusException responseStatusException = (ResponseStatusException) exception;
				assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			});
	}

	@Test
	void rejectsShareRequestWithoutUrl() {
		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(ItemInputSource.SHARE, null, null, null, null, null)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> {
				ResponseStatusException responseStatusException = (ResponseStatusException) exception;
				assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			});
	}

	@Test
	void rejectsShareUrlWithUserInfo() {
		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://user:password@shopping.example.com/products/100",
				null,
				null,
				null,
				null
			)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> {
				ResponseStatusException responseStatusException = (ResponseStatusException) exception;
				assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			});
	}

	@Test
	void classifiesProductKeywordBeforeOliveYoungDomainSignal() {
		String url = "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A0000001";
		pageFetcher.stub(url, productHtml("고소한 베이글칩 간식 세트", "바삭한 스낵과 부각 구성"));

		ShoppingLinkImportResponse response = importSharedLink(url);

		assertThat(response.item().category()).isEqualTo(ItemCategory.LIVING);
		assertThat(response.item().categoryConfidence()).isEqualTo(0.70d);
	}

	@Test
	void classifiesProductKeywordBeforeMultiCategoryStoreSignal() {
		String url = "https://www.29cm.co.kr/product/123456";
		pageFetcher.stub(url, productHtml("전자동 커피머신", "주방에서 사용하는 홈카페 생활가전"));

		ShoppingLinkImportResponse response = importSharedLink(url);

		assertThat(response.item().category()).isEqualTo(ItemCategory.LIVING);
		assertThat(response.item().categoryConfidence()).isEqualTo(0.70d);
	}

	@Test
	void usesFashionDomainSignalWhenProductKeywordIsWeak() {
		String url = "https://www.musinsa.com/products/987654";
		pageFetcher.stub(url, productHtml("단독 특가 상품", "오늘만 제공되는 한정 혜택"));

		ShoppingLinkImportResponse response = importSharedLink(url);

		assertThat(response.item().category()).isEqualTo(ItemCategory.FASHION);
		assertThat(response.item().categoryConfidence()).isEqualTo(0.25d);
	}

	@Test
	void classifiesDigitalKeywordBeforeHobbyKeyword() {
		String url = "https://globalbunjang.com/product/337816518";
		pageFetcher.stub(url, productHtml("무선 게이밍 키보드", "게임과 취미용으로 쓰기 좋은 키보드"));

		ShoppingLinkImportResponse response = importSharedLink(url);

		assertThat(response.item().category()).isEqualTo(ItemCategory.DIGITAL);
		assertThat(response.item().categoryConfidence()).isEqualTo(0.70d);
	}

	@Test
	void mapsUnsupportedDetailedCategoryToEtcRecommendation() {
		String url = "https://example.com/products/monthly-membership";
		pageFetcher.stub(url, productHtml("월간 정기구독 이용권", "멤버십과 기프트카드 혜택"));

		ShoppingLinkImportResponse response = importSharedLink(url);

		assertThat(response.item().category()).isEqualTo(ItemCategory.ETC);
		assertThat(response.item().categoryConfidence()).isNull();
	}

	private ShoppingLinkImportResponse importSharedLink(String url) {
		return service.importLink(
			new ShoppingLinkImportRequest(ItemInputSource.SHARE, url, null, null, null, null)
		);
	}

	private String productHtml(String title, String description) {
		return """
			<html>
			<head>
			  <meta property="og:title" content="%s" />
			  <meta property="og:description" content="%s" />
			  <meta property="og:image" content="https://cdn.example.com/product.jpg" />
			  <meta property="product:price:amount" content="10000" />
			</head>
			<body></body>
			</html>
			""".formatted(title, description);
	}

	private static final class FakePageFetcher implements PageFetcher {

		private final Map<String, String> pages = new java.util.HashMap<>();

		private void stub(String url, String body) {
			pages.put(url, body);
		}

		@Override
		public FetchedPage fetch(URI uri) {
			String body = pages.get(uri.toString());
			if (body == null) {
				throw new AssertionError("No stubbed page for " + uri);
			}
			return new FetchedPage(uri, uri, 200, "text/html", body);
		}
	}
}
