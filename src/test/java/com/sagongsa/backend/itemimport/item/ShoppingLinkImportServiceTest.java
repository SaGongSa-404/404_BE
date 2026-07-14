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
	void parsesDecimalJsonLdPriceWithoutAppendingFractionDigits() {
		pageFetcher.stub(
			"https://www.daangn.com/articles/1200892330",
			"""
				<html>
				<head>
				  <script type="application/ld+json">
				  {
				    "@context": "https://schema.org",
				    "@type": "Product",
				    "name": "나이키 운동화",
				    "image": "https://dnvefa72aowie.cloudfront.net/product.jpg",
				    "offers": {
				      "@type": "Offer",
				      "price": "18000.0"
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
				"https://www.daangn.com/articles/1200892330",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.retrievalStatus()).isEqualTo("SUCCESS");
		assertThat(response.item().title()).isEqualTo("나이키 운동화");
		assertThat(response.item().listedPrice()).isEqualTo(18000);
		assertThat(response.item().imageUrl()).isEqualTo("https://dnvefa72aowie.cloudfront.net/product.jpg");
	}

	@Test
	void prefersDaangnProductJsonLdOverSocialCardMetadata() {
		pageFetcher.stub(
			"https://www.daangn.com/kr/buy-sell/orute-coffee-machine-r5fvp77gm767/",
			"""
				<html>
				<head>
				  <meta property="og:title" content="오르테 커피머신 OCK-352B | 생활가전 | 당근 중고거래" />
				  <meta property="og:image" content="https://dnvefa72aowie.cloudfront.net/cover.jpg?s=1200x630&amp;t=cover" />
				  <script type="application/ld+json">
				  {
				    "@context": "https://schema.org",
				    "@type": "Product",
				    "name": "오르테 커피머신 OCK-352B",
				    "image": "https://dnvefa72aowie.cloudfront.net/product.jpg?s=1440x1440&t=inside",
				    "offers": {
				      "@type": "Offer",
				      "price": "95000",
				      "priceCurrency": "KRW"
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
				"https://www.daangn.com/kr/buy-sell/orute-coffee-machine-r5fvp77gm767/",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.retrievalStatus()).isEqualTo("SUCCESS");
		assertThat(response.item().title()).isEqualTo("오르테 커피머신 OCK-352B");
		assertThat(response.item().listedPrice()).isEqualTo(95000);
		assertThat(response.item().currencyCode()).isEqualTo("KRW");
		assertThat(response.item().imageUrl()).isEqualTo("https://dnvefa72aowie.cloudfront.net/product.jpg?s=1440x1440&t=inside");
		assertThat(response.sourceMetadata().extractionMethod()).isEqualTo("JSON_LD");
	}

	@Test
	void rejectsForeignCurrencyForVerifiedKoreanProductUrl() {
		pageFetcher.stub(
			"https://www.daangn.com/kr/buy-sell/foreign-currency-product/",
			"""
				<html><head>
				  <script type="application/ld+json">
				  {
				    "@type": "Product",
				    "name": "외화 상품",
				    "image": "https://images.example.com/product.jpg",
				    "offers": {"price": "100", "priceCurrency": "USD"}
				  }
				  </script>
				</head></html>
				"""
		);

		assertThatThrownBy(() -> service.importLink(new ShoppingLinkImportRequest(
			ItemInputSource.SHARE,
			"https://www.daangn.com/kr/buy-sell/foreign-currency-product/",
			null, null, null, null
		)))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
	}

	@Test
	void rejectsDecimalKrwPriceForVerifiedKoreanProductUrl() {
		pageFetcher.stub(
			"https://www.daangn.com/kr/buy-sell/decimal-price-product/",
			"""
				<html><head>
				  <script type="application/ld+json">
				  {
				    "@type": "Product",
				    "name": "소수 가격 상품",
				    "image": "https://images.example.com/product.jpg",
				    "offers": {"price": "100.50", "priceCurrency": "KRW"}
				  }
				  </script>
				</head></html>
				"""
		);

		assertThatThrownBy(() -> service.importLink(new ShoppingLinkImportRequest(
			ItemInputSource.SHARE,
			"https://www.daangn.com/kr/buy-sell/decimal-price-product/",
			null, null, null, null
		)))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
	}

	@Test
	void importsVerifiedZigzagProductUsingCleanTitleCurrentKrwPriceAndProductImage() {
		String url = "https://zigzag.kr/catalog/products/141911042?catalog_product_id=141911042";
		pageFetcher.stub(
			url,
			"""
				<html><head>
				  <meta property="og:title" content="리얼코코 [S-XL/5만장돌파🏆/made] 모티브 핀턱 슬랙스 (숏/기본)" />
				  <meta property="product:price:amount" content="24,010" />
				  <meta property="product:price:currency" content="KRW" />
				  <script type="application/ld+json">
				  {
				    "@type": "Product",
				    "name": "리얼코코 [S-XL/5만장돌파]]>&#x1f3c6;<![CDATA[/made] 모티브 핀턱 슬랙스 (숏/기본)",
				    "image": ["https://cf.product-image.s.zigzag.kr/product.jpeg?width=720&amp;height=720"]
				  }
				  </script>
				  <script>
				  window.__STATE__ = {
				    "product": {
				      "name": "[S-XL/5만장돌파🏆/made] 모티브 핀턱 슬랙스 (숏/기본)",
				      "max_price_info": {"price": 63000},
				      "display_final_price": {"final_price": {"price": 30020}}
				    }
				  };
				  </script>
				</head><body></body></html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(new ShoppingLinkImportRequest(
			ItemInputSource.SHARE, url, null, null, null, null
		));

		assertThat(response.retrievalStatus()).isEqualTo("SUCCESS");
		assertThat(response.item().title())
			.isEqualTo("리얼코코 [S-XL/5만장돌파🏆/made] 모티브 핀턱 슬랙스 (숏/기본)");
		assertThat(response.item().listedPrice()).isEqualTo(24010);
		assertThat(response.item().currencyCode()).isEqualTo("KRW");
		assertThat(response.item().imageUrl())
			.isEqualTo("https://cf.product-image.s.zigzag.kr/product.jpeg?width=720&height=720");
	}

	@Test
	void reportsPartialWhenPriceIsMissing() {
		pageFetcher.stub(
			"https://shopping.example.com/products/missing-price",
			"""
				<html><head>
				  <meta property="og:title" content="가격 누락 상품" />
				  <meta property="og:image" content="https://cdn.example.com/item.jpg" />
				</head><body></body></html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://shopping.example.com/products/missing-price",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.retrievalStatus()).isEqualTo("PARTIAL");
		assertThat(response.item().listedPrice()).isNull();
	}

	@Test
	void reportsPartialWhenImageIsMissing() {
		pageFetcher.stub(
			"https://shopping.example.com/products/missing-image",
			"""
				<html><head>
				  <meta property="og:title" content="이미지 누락 상품" />
				  <meta property="product:price:amount" content="18,000원" />
				</head><body></body></html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://shopping.example.com/products/missing-image",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.retrievalStatus()).isEqualTo("PARTIAL");
		assertThat(response.item().listedPrice()).isEqualTo(18000);
		assertThat(response.item().imageUrl()).isNull();
	}

	@Test
	void reportsPartialWhenImageUrlHasNoHost() {
		pageFetcher.stub(
			"https://shopping.example.com/products/invalid-image",
			"""
				<html><head>
				  <meta property="og:title" content="잘못된 이미지 상품" />
				  <meta property="og:image" content="https:missing-host.jpg" />
				  <meta property="product:price:amount" content="18000" />
				</head><body></body></html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://shopping.example.com/products/invalid-image",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.retrievalStatus()).isEqualTo("PARTIAL");
		assertThat(response.item().imageUrl()).isNull();
	}

	@Test
	void neverReportsSuccessWhenTitleIsMissing() {
		pageFetcher.stub(
			"https://shopping.example.com/products/missing-title",
			"""
				<html><head>
				  <meta property="og:image" content="https://cdn.example.com/item.jpg" />
				  <meta property="product:price:amount" content="18000" />
				</head><body></body></html>
				"""
		);

		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://shopping.example.com/products/missing-title",
				null,
				null,
				null,
				null
			)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
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
	void prefersOliveYoungSalePriceOverOriginalPrice() {
		pageFetcher.stub(
			"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000137482",
			"""
				<html>
				<head>
				  <meta property="og:title" content="로벡틴 카밍 연꽃수 크림 60ml | 올리브영" />
				  <meta property="og:image" content="https://image.oliveyoung.co.kr/item.png" />
				  <meta property="product:price:amount" content="24000" />
				  <meta property="eg:originalPrice" content="24000" />
				  <meta property="eg:salePrice" content="18000" />
				</head>
				<body></body>
				</html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000137482",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().listedPrice()).isEqualTo(18000);
		assertThat(response.saveRequest().listedPrice()).isEqualTo(18000);
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
	void acceptsProductPageWhenBodyContainsWaitPhrase() {
		pageFetcher.stub(
			"https://shopping.example.com/products/waiting-note",
			"""
				<html>
				<head>
				  <title>기다림 노트 상품 상세</title>
				  <meta property="og:title" content="기다림 노트 세트" />
				  <meta property="og:image" content="https://cdn.example.com/waiting-note.jpg" />
				  <meta property="product:price:amount" content="18000" />
				</head>
				<body>
				  이 상품은 배송 전 잠시만 기다려 주세요 라는 안내 문구가 포함된 패키지입니다.
				  실제 상품 상세, 사용 방법, 구성품, 교환 안내, 리뷰 요약까지 정상적으로 렌더링된 긴 본문입니다.
				  차단 안내 페이지가 아니라 상품 설명 문장 안에 같은 표현이 포함된 케이스입니다.
				</body>
				</html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://shopping.example.com/products/waiting-note",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().title()).isEqualTo("기다림 노트 세트");
		assertThat(response.item().listedPrice()).isEqualTo(18000);
		assertThat(response.item().imageUrl()).isEqualTo("https://cdn.example.com/waiting-note.jpg");
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
				  <meta property="product:price:currency" content="KRW" />
				  <script>
				  window.__MSS_FE__ = {
				    "product": {
				      "state": {
				        "goodsNm": "코치 자켓 [블랙]",
				        "salePrice": 129000,
				        "thumbnailImageUrl": "https://image.msscdn.net/item.jpg"
				      }
				    }
				  };
				  </script>
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

		assertThat(response.item().title()).isEqualTo("코치 자켓 [블랙]");
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
	void extractsNaverCommerceMetadataFromKakaoCommerceTags() {
		pageFetcher.stub(
			"https://m.brand.naver.com/cookierun/products/13194003181?tr=nshfum",
			"""
				<html>
				<head>
				  <meta property="og:title" content="[쿠키런스토어] 쿠키런 굿나잇 보조배터리 3종 : 쿠키런스토어" />
				  <meta property="og:image" content="https://shop-phinf.pstatic.net/product.png" />
				  <meta property="kakao:commerce:brand_name" content="쿠키런" />
				  <meta property="kakao:commerce:price" content="17910" />
				</head>
				<body></body>
				</html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://m.brand.naver.com/cookierun/products/13194003181?tr=nshfum",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().title()).isEqualTo("[쿠키런스토어] 쿠키런 굿나잇 보조배터리 3종 : 쿠키런스토어");
		assertThat(response.item().brandName()).isEqualTo("쿠키런");
		assertThat(response.item().listedPrice()).isEqualTo(17910);
		assertThat(response.item().category()).isEqualTo(ItemCategory.DIGITAL);
	}

	@Test
	void rejectsOliveYoungDetailPathWithoutGoodsNumber() {
		String url = "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do";
		pageFetcher.stub(url, completeProductMetadata("올리브영 테스트 상품"));

		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(ItemInputSource.SHARE, url, null, null, null, null)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
	}

	@Test
	void rejectsNaverBrandStorePageThatIsNotAProductDetail() {
		String url = "https://brand.naver.com/cookierun/category/all";
		pageFetcher.stub(url, completeProductMetadata("네이버 테스트 상품"));

		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(ItemInputSource.SHARE, url, null, null, null, null)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
	}

	@Test
	void acceptsSmartStoreAndKreamProductDetailPaths() {
		String smartStoreUrl = "https://smartstore.naver.com/sample/products/11933395125";
		String kreamUrl = "https://kream.co.kr/products/444045";
		pageFetcher.stub(smartStoreUrl, completeProductMetadata("스마트스토어 테스트 상품"));
		pageFetcher.stub(kreamUrl, completeProductMetadata("크림 테스트 상품"));

		ShoppingLinkImportResponse smartStore = service.importLink(
			new ShoppingLinkImportRequest(ItemInputSource.SHARE, smartStoreUrl, null, null, null, null)
		);
		ShoppingLinkImportResponse kream = service.importLink(
			new ShoppingLinkImportRequest(ItemInputSource.SHARE, kreamUrl, null, null, null, null)
		);

		assertThat(smartStore.item().title()).isEqualTo("스마트스토어 테스트 상품");
		assertThat(kream.item().title()).isEqualTo("크림 테스트 상품");
	}

	@Test
	void rejectsSmartStoreAndKreamPagesThatAreNotProductDetails() {
		String smartStoreUrl = "https://smartstore.naver.com/sample/category/all";
		String kreamUrl = "https://kream.co.kr/search?keyword=nike";
		pageFetcher.stub(smartStoreUrl, completeProductMetadata("스마트스토어 목록"));
		pageFetcher.stub(kreamUrl, completeProductMetadata("크림 검색"));

		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(ItemInputSource.SHARE, smartStoreUrl, null, null, null, null)
		)).isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(ItemInputSource.SHARE, kreamUrl, null, null, null, null)
		)).isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
	}

	@Test
	void rejectsVerifiedProductThatRedirectsToLoginDomain() {
		String url = "https://smartstore.naver.com/sample/products/11933395125";
		ShoppingLinkImportService redirectingService = new ShoppingLinkImportService(
			requestedUri -> new FetchedPage(
				requestedUri,
				URI.create("https://nid.naver.com/nidlogin.login"),
				200,
				"text/html",
				completeProductMetadata("네이버 로그인")
			),
			new ObjectMapper()
		);

		assertThatThrownBy(() -> redirectingService.importLink(
			new ShoppingLinkImportRequest(ItemInputSource.SHARE, url, null, null, null, null)
		)).isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
	}

	private String completeProductMetadata(String title) {
		return """
			<html><head>
			<meta property="og:title" content="%s" />
			<meta property="og:image" content="https://example.com/product.jpg" />
			<meta property="product:price:amount" content="12900" />
			<meta property="product:price:currency" content="KRW" />
			</head><body></body></html>
			""".formatted(title);
	}

	@Test
	void extractsProductMetadataFromEmbeddedCommerceJson() {
		pageFetcher.stub(
			"https://zigzag.kr/catalog/products/110621280",
			"""
				<html>
				<head>
				  <title>지그재그</title>
				  <script id="__NEXT_DATA__" type="application/json">
				  {
				    "props": {
				      "pageProps": {
				        "product": {
				          "productName": "린넨 반팔 셔츠",
				          "salePrice": 39000,
				          "imageUrl": "https://image.zigzag.kr/product.jpg",
				          "description": "여름 셔츠 상품 상세"
				        }
				      }
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
				"https://zigzag.kr/catalog/products/110621280",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().title()).isEqualTo("린넨 반팔 셔츠");
		assertThat(response.item().listedPrice()).isEqualTo(39000);
		assertThat(response.item().imageUrl()).isEqualTo("https://image.zigzag.kr/product.jpg");
		assertThat(response.item().category()).isEqualTo(ItemCategory.FASHION);
		assertThat(response.sourceMetadata().extractionMethod()).isEqualTo("EMBEDDED_JSON");
	}

	@Test
	void prefersProductObjectOverGenericEmbeddedJsonKeys() {
		pageFetcher.stub(
			"https://zigzag.kr/catalog/products/220",
			"""
				<html>
				<head>
				  <title>지그재그</title>
				  <script id="__NEXT_DATA__" type="application/json">
				  {
				    "props": {
				      "pageProps": {
				        "seo": {
				          "title": "지그재그 인기 상품",
				          "amount": 999999,
				          "imageUrl": "https://image.zigzag.kr/seo.jpg"
				        },
				        "product": {
				          "name": "오버핏 코튼 셔츠",
				          "price": 45000,
				          "image": "https://image.zigzag.kr/cotton-shirt.jpg",
				          "description": "상품 상세 설명"
				        }
				      }
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
				"https://zigzag.kr/catalog/products/220",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().title()).isEqualTo("오버핏 코튼 셔츠");
		assertThat(response.item().listedPrice()).isEqualTo(45000);
		assertThat(response.item().imageUrl()).isEqualTo("https://image.zigzag.kr/cotton-shirt.jpg");
		assertThat(response.item().category()).isEqualTo(ItemCategory.FASHION);
	}

	@Test
	void ignoresZeroEmbeddedPriceAndUsesLaterProductPrice() {
		pageFetcher.stub(
			"https://m.bunjang.co.kr/products/123",
			"""
				<html>
				<head>
				  <title>번개장터</title>
				  <script>
				  window.__PRELOADED_STATE__ = {
				    "product": {
				      "name": null,
				      "salePrice": 0,
				      "discountedSalePrice": 0,
				      "representativeImageUrl": null
				    },
				    "productDetail": {
				      "item": {
				        "name": "나이키 반팔 티셔츠",
				        "price": 29000,
				        "image": "https://media.bunjang.co.kr/product.jpg"
				      }
				    }
				  };
				  </script>
				</head>
				<body></body>
				</html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://m.bunjang.co.kr/products/123",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().title()).isEqualTo("나이키 반팔 티셔츠");
		assertThat(response.item().listedPrice()).isEqualTo(29000);
		assertThat(response.item().imageUrl()).isEqualTo("https://media.bunjang.co.kr/product.jpg");
		assertThat(response.item().category()).isEqualTo(ItemCategory.FASHION);
	}

	@Test
	void classifiesBunjangSportsProductWithoutLipFalsePositive() {
		pageFetcher.stub(
			"https://m.bunjang.co.kr/products/398473587",
			"""
				<html>
				<head>
				  <meta property="og:title" content="용인대 아디다스 유도복 165" />
				  <meta property="og:description" content="수선하시면 될것같고 그거 감안해서 싸게 올립니다" />
				  <meta property="og:image" content="https://media.bunjang.co.kr/product/398473587_1_1781014735_w900.jpg" />
				  <meta property="product:price:amount" content="100000" />
				</head>
				<body></body>
				</html>
				"""
		);

		ShoppingLinkImportResponse response = service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://m.bunjang.co.kr/products/398473587",
				null,
				null,
				null,
				null
			)
		);

		assertThat(response.item().title()).isEqualTo("용인대 아디다스 유도복 165");
		assertThat(response.item().listedPrice()).isEqualTo(100000);
		assertThat(response.item().category()).isEqualTo(ItemCategory.HOBBY);
	}

	@Test
	void rejectsCommerceBridgeShellInsteadOfImportingSiteNameWithZeroPrice() {
		pageFetcher.stub(
			"https://zigzag.kr/share/products/110621280",
			"""
				<html>
				<head>
				  <title>지그재그</title>
				  <meta property="og:title" content="지그재그" />
				  <meta property="og:image" content="https://cf.res.s.zigzag.kr/app-icon.png" />
				  <script>
				  window.__PRELOADED_STATE__ = {
				    "product": {
				      "salePrice": 0,
				      "discountedSalePrice": 0
				    }
				  };
				  </script>
				</head>
				<body></body>
				</html>
				"""
		);

		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://zigzag.kr/share/products/110621280",
				null,
				null,
				null,
				null
			)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> {
				ResponseStatusException responseStatusException = (ResponseStatusException) exception;
				assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
			});
	}

	@Test
	void rejectsZigzagAirbridgeShellInsteadOfImportingSiteName() {
		pageFetcher.stub(
			"https://zigzag.airbridge.io/open/product_detail?fallback_desktop=https%3A%2F%2Fzigzag.kr%2Fp%2F170411267",
			"""
				<html>
				<head>
				  <title>지그재그</title>
				  <meta property="og:title" content="지그재그" />
				  <meta property="og:description" content="4,000만 여성이 선택한 쇼핑앱, 지그재그" />
				  <meta property="og:image" content="http://static.airbridge.io/images/og_tags/zigzag.png" />
				</head>
				<body></body>
				</html>
				"""
		);

		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://zigzag.airbridge.io/open/product_detail?fallback_desktop=https%3A%2F%2Fzigzag.kr%2Fp%2F170411267",
				null,
				null,
				null,
				null
			)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> {
				ResponseStatusException responseStatusException = (ResponseStatusException) exception;
				assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
			});
	}

	@Test
	void rejectsBunjangAirbridgeShellInsteadOfImportingSiteName() {
		pageFetcher.stub(
			"https://bunjang.airbridge.io/goto?type=product&val=398473587",
			"""
				<html>
				<head>
				  <title>번개장터</title>
				  <meta property="og:title" content="번개장터" />
				  <meta property="og:description" content="취향을 잇는 거래, 번개장터" />
				  <meta property="og:image" content="https://static.bunjang.co.kr/images/logo.png" />
				</head>
				<body></body>
				</html>
				"""
		);

		assertThatThrownBy(() -> service.importLink(
			new ShoppingLinkImportRequest(
				ItemInputSource.SHARE,
				"https://bunjang.airbridge.io/goto?type=product&val=398473587",
				null,
				null,
				null,
				null
			)
		))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> {
				ResponseStatusException responseStatusException = (ResponseStatusException) exception;
				assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
			});
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
