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
	void skipsOliveYoungSiteTitleAndUsesProductTitleFallback() {
		pageFetcher.stub(
			"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000230109",
			"""
				<html>
				<head>
				  <title>[올리브영]</title>
				  <meta property="og:title" content="/ 올리브영" />
				  <meta property="og:image" content="https://image.oliveyoung.co.kr/item.jpg" />
				  <meta property="product:price:amount" content="12900" />
				</head>
				<body>
				  <h1>[포켓몬 에디션] 힐링버드 헤어에센스 150ml / 올리브영</h1>
				</body>
				</html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000230109",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().title()).isEqualTo("[포켓몬 에디션] 힐링버드 헤어에센스 150ml");
		assertThat(response.saveRequest().title()).isEqualTo("[포켓몬 에디션] 힐링버드 헤어에센스 150ml");
		assertThat(response.item().category()).isEqualTo(ItemCategory.BEAUTY);
	}

	@Test
	void rejectsOliveYoungChallengePageInsteadOfUsingChallengeTitle() {
		pageFetcher.stub(
			"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000230109",
			"""
				<html>
				<head>
				  <title>잠시만 기다려 주세요 - 올리브영</title>
				  <meta property="og:image" content="https://image.oliveyoung.co.kr/item.jpg" />
				  <meta property="product:price:amount" content="12900" />
				</head>
				<body>안전하고 원활한 올리브영 이용을 위해 접속 정보를 확인 중이에요</body>
				</html>
				"""
		);

		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000230109",
				null,
				null,
				null,
				null
			)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> {
				ResponseStatusException responseStatusException = (ResponseStatusException) exception;
				assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
			});
	}

	@Test
	void acceptsRenderedProductPageWithCloudflareScriptMarker() {
		pageFetcher.stub(
			"https://www.musinsa.com/products/478021",
			"""
				<html>
				<head>
				  <title>키르시 코치 자켓 | 무신사</title>
				  <meta property="og:title" content="키르시 코치 자켓 [블랙] - 사이즈 & 후기 | 무신사" />
				  <meta property="og:image" content="https://image.msscdn.net/item.jpg" />
				  <meta property="product:price:amount" content="129000" />
				  <script src="/cdn-cgi/challenge-platform/h/b/scripts/jsd/main.js"></script>
				</head>
				<body>
				  MUSINSA BEAUTY SPORTS OUTLET BOUTIQUE KICKS KIDS USED SNAP
				  키르시 코치 자켓 상품 정보와 사이즈 추천, 스냅 후기, 문의 영역이 정상적으로 렌더링된 페이지입니다.
				</body>
				</html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://www.musinsa.com/products/478021",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().title()).isEqualTo("키르시 코치 자켓 [블랙] - 사이즈 & 후기 | 무신사");
		assertThat(response.item().listedPrice()).isEqualTo(129000);
		assertThat(response.item().imageUrl()).isEqualTo("https://image.msscdn.net/item.jpg");
		assertThat(response.item().category()).isEqualTo(ItemCategory.FASHION);
	}

	@Test
	void acceptsAblySearchPageWithCloudflareMarkerAndRenderedTitle() {
		pageFetcher.stub(
			"https://m.a-bly.com/search?keyword=%EA%B0%80%EB%94%94%EA%B1%B4",
			"""
				<html>
				<head>
				  <title>가디건 - 에이블리 스토어</title>
				  <meta property="og:title" content="가디건 - 에이블리 스토어" />
				  <meta property="og:image" content="https://img.a-bly.com/og_image.jpg" />
				  <script>window.__cf_chl_opt = {};</script>
				</head>
				<body>
				  앱에서 더 많은 상품을 볼 수 있어요! 앱에서 보기 찜할 서랍 선택 새 서랍 만들기 보러가기 새 서랍 만들기 완료
				</body>
				</html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://m.a-bly.com/search?keyword=%EA%B0%80%EB%94%94%EA%B1%B4",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().title()).isEqualTo("가디건 - 에이블리 스토어");
		assertThat(response.item().imageUrl()).isEqualTo("https://img.a-bly.com/og_image.jpg");
		assertThat(response.item().category()).isEqualTo(ItemCategory.FASHION);
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
	void rejectsShareUrlWithUnsupportedScheme() {
		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"javascript:alert(1)",
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
	void rejectsDirectInputUrlWithUserInfo() {
		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.DIRECT_INPUT,
				"https://user:password@shopping.example.com/products/100",
				"수동 입력 상품",
				null,
				1000,
				null
			)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> {
				ResponseStatusException responseStatusException = (ResponseStatusException) exception;
				assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			});
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
