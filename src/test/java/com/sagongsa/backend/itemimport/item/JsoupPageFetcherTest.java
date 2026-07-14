package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

class JsoupPageFetcherTest {

	@Test
	void resolvesAppsFlyerOneLinkStoreLinkToProductPage() {
		String body = """
			<html>
			<head>
			  <script>
			    var store_link = 'https://www.musinsa.com/products/5441563?af_channel=mobile_share&shortlink=tefhk0o4';
			    var web_store_link = 'https://apps.apple.com/KR/app/id1003139529?mt=8';
			    var app_link = 'musinsaad://web?link=https%3A%2F%2Fwww.musinsa.com%2Fproducts%2F5441563';
			  </script>
			</head>
			<body></body>
			</html>
			""";

		assertThat(JsoupPageFetcher.clientSideRedirectTarget(
			URI.create("https://musinsa.onelink.me/PvkC/tefhk0o4"),
			"text/html; charset=utf-8",
			body
		)).contains(URI.create("https://www.musinsa.com/products/5441563?af_channel=mobile_share&shortlink=tefhk0o4"));
	}

	@Test
	void resolvesAppsFlyerAppLinkQueryWhenStoreLinkIsMissing() {
		String body = """
			<html>
			<head>
			  <script>
			    var web_store_link = 'https://apps.apple.com/KR/app/id1003139529?mt=8';
			    var app_link = 'musinsaad://web?link=https%3A%2F%2Fwww.musinsa.com%2Fproducts%2F5441563&af_ios_url=https%3A%2F%2Fwww.musinsa.com%2Fproducts%2F5441563';
			  </script>
			</head>
			<body></body>
			</html>
			""";

		assertThat(JsoupPageFetcher.clientSideRedirectTarget(
			URI.create("https://musinsa.onelink.me/PvkC/tefhk0o4"),
			"text/html",
			body
		)).contains(URI.create("https://www.musinsa.com/products/5441563"));
	}

	@Test
	void doesNotResolveAppStoreFallbackAsShoppingPage() {
		String body = """
			<html>
			<head>
			  <meta http-equiv="refresh" content="0; url=https://apps.apple.com/KR/app/id1003139529?mt=8" />
			</head>
			<body></body>
			</html>
			""";

		assertThat(JsoupPageFetcher.clientSideRedirectTarget(
			URI.create("https://musinsa.onelink.me/PvkC/tefhk0o4"),
			"text/html",
			body
		)).isEmpty();
	}

	@Test
	void resolvesNaverShoppingBridgeQueryUrl() {
		String body = """
			<html>
			<head><title>네이버+ 스토어</title></head>
			<body>
			  <script id="__NEXT_DATA__" type="application/json">
			  {
			    "page": "/app-bridge",
			    "query": {
			      "url": "https://m.brand.naver.com/cookierun/products/13194003181?NaPm=ct%3Dabc\\u0026tr=nshfum"
			    }
			  }
			  </script>
			</body>
			</html>
			""";

		assertThat(JsoupPageFetcher.clientSideRedirectTarget(
			URI.create("https://app.shopping.naver.com/app-bridge?url=https%3A%2F%2Fm.brand.naver.com%2Fcookierun%2Fproducts%2F13194003181"),
			"text/html",
			body
		)).contains(URI.create("https://m.brand.naver.com/cookierun/products/13194003181?tr=nshfum"));
	}

	@Test
	void resolvesNaverShoppingBridgeDeepLinkUrl() {
		String body = """
			<html>
			<body>
			  <script id="__NEXT_DATA__" type="application/json">
			  {
			    "page": "/app-bridge",
			    "query": {
			      "dst": "navershopping://open?url=https%3A%2F%2Fm.brand.naver.com%2Fcookierun%2Fproducts%2F13194003181%3Ftr%3Dnshfum"
			    }
			  }
			  </script>
			</body>
			</html>
			""";

		assertThat(JsoupPageFetcher.clientSideRedirectTarget(
			URI.create("https://app.shopping.naver.com/bridge"),
			"text/html",
			body
		)).contains(URI.create("https://m.brand.naver.com/cookierun/products/13194003181?tr=nshfum"));
	}

	@Test
	void doesNotTreatProductPageEmbeddedUrlAsBridgeRedirect() {
		String body = """
			<html>
			<body>
			  <script>
			    window.__PRELOADED_STATE__ = {
			      "channel": {
			        "url": "https://brand.naver.com/cookierun"
			      }
			    };
			  </script>
			</body>
			</html>
			""";

		assertThat(JsoupPageFetcher.clientSideRedirectTarget(
			URI.create("https://m.brand.naver.com/cookierun/products/13194003181"),
			"text/html",
			body
		)).isEmpty();
	}

	@Test
	void resolvesCommerceAppDeepLinkVariableToWebUrl() {
		String body = """
			<html>
			<head>
			  <script>
			    var deep_link = 'bunjang://product?id=123&url=https%3A%2F%2Fm.bunjang.co.kr%2Fproducts%2F123';
			  </script>
			</head>
			<body></body>
			</html>
			""";

		assertThat(JsoupPageFetcher.clientSideRedirectTarget(
			URI.create("https://link.bunjang.co.kr/product/123"),
			"text/html",
			body
		)).contains(URI.create("https://m.bunjang.co.kr/products/123"));
	}

	@Test
	void resolvesOliveYoungShortLinkServerDataTargetUrl() {
		String body = """
			<html>
			<head>
			  <script>
			    window.__SERVER_DATA__ = {
			      "targetUrl": "https://m.oliveyoung.co.kr/m/goods/getGoodsDetail.do?goodsNo=A000000186792",
			      "status": 200
			    };
			  </script>
			</head>
			<body></body>
			</html>
			""";

		assertThat(JsoupPageFetcher.clientSideRedirectTarget(
			URI.create("https://oy.run/0JF05CMTNr33Nl"),
			"text/html",
			body
		)).contains(URI.create("https://m.oliveyoung.co.kr/m/goods/getGoodsDetail.do?goodsNo=A000000186792"));
	}

	@Test
	void resolvesAirbridgeShareLinkQueryToZigzagProductPage() {
		assertThat(JsoupPageFetcher.queryRedirectTarget(
			URI.create("https://abr.ge/@zigzag/sharelink"
				+ "?deeplink_url=zigzag%3A%2F%2Fopen%2Fproduct_detail%3Furl%3Dhttps%253A%252F%252Fstore.zigzag.kr%252Fcatalog%252Fproducts%252F170411267%253Fcatalog_product_id%253D170411267%26catalog_product_id%3D170411267"
				+ "&fallback_desktop=https%3A%2F%2Fzigzag.kr%2Fp%2F170411267")
		)).contains(URI.create("https://store.zigzag.kr/catalog/products/170411267?catalog_product_id=170411267"));
	}

	@Test
	void resolvesAirbridgeProductDetailQueryToZigzagProductPage() {
		assertThat(JsoupPageFetcher.queryRedirectTarget(
			URI.create("https://zigzag.airbridge.io/open/product_detail"
				+ "?url=https%3A%2F%2Fstore.zigzag.kr%2Fcatalog%2Fproducts%2F170411267%3Fcatalog_product_id%3D170411267"
				+ "&catalog_product_id=170411267"
				+ "&fallback_desktop=https%3A%2F%2Fzigzag.kr%2Fp%2F170411267")
		)).contains(URI.create("https://store.zigzag.kr/catalog/products/170411267?catalog_product_id=170411267"));
	}

	@Test
	void resolvesBunjangAirbridgeProductQueryToMobileProductPage() {
		assertThat(JsoupPageFetcher.queryRedirectTarget(
			URI.create("https://bunjang.airbridge.io/goto"
				+ "?type=product"
				+ "&val=398473587"
				+ "&airbridge_referrer=airbridge%3Dtrue")
		)).contains(URI.create("https://m.bunjang.co.kr/products/398473587"));
	}

	@Test
	void buildsBunjangProductMetadataHtmlFromDetailApiResponse() {
		String apiBody = """
			{
			  "data": {
			    "product": {
			      "pid": 398473587,
			      "name": "용인대 아디다스 유도복 165",
			      "description": "용인대 마크가 새겨진 아디다스 유도복",
			      "price": 100000,
			      "imageUrl": "https://media.bunjang.co.kr/product/398473587_{cnt}_1781014735_w{res}.jpg",
			      "brand": {
			        "name": "아디다스"
			      }
			    }
			  }
			}
			""";

		String html = JsoupPageFetcher.bunjangProductMetadataHtml(apiBody).orElseThrow();
		Document document = Jsoup.parse(html);

		assertThat(document.selectFirst("meta[property=og:title]").attr("content"))
			.isEqualTo("용인대 아디다스 유도복 165");
		assertThat(document.selectFirst("meta[property=product:price:amount]").attr("content"))
			.isEqualTo("100000");
		assertThat(document.selectFirst("meta[property=og:image]").attr("content"))
			.isEqualTo("https://media.bunjang.co.kr/product/398473587_1_1781014735_w900.jpg");
		assertThat(document.selectFirst("meta[property=kakao:commerce:brand_name]").attr("content"))
			.isEqualTo("아디다스");
	}

	@Test
	void buildsKreamProductMetadataHtmlFromWebApiResponse() {
		String apiBody = """
			{
			  "release": {
			    "id": 444045,
			    "name": "(W) Nike LD-1000 Summit White Sail",
			    "translated_name": "(W) 나이키 LD-1000 서밋 화이트 세일",
			    "image_urls": ["https://kream-phinf.pstatic.net/product.png"],
			    "brand": {
			      "name": "Nike",
			      "translated_name": "나이키"
			    }
			  },
			  "market": {
			    "lowest_ask": 33000.0
			  }
			}
			""";

		String html = JsoupPageFetcher.kreamProductMetadataHtml(apiBody).orElseThrow();
		Document document = Jsoup.parse(html);

		assertThat(document.selectFirst("meta[property=og:title]").attr("content"))
			.isEqualTo("(W) 나이키 LD-1000 서밋 화이트 세일");
		assertThat(document.selectFirst("meta[property=product:price:amount]").attr("content"))
			.isEqualTo("33000.0");
		assertThat(document.selectFirst("meta[property=og:image]").attr("content"))
			.isEqualTo("https://kream-phinf.pstatic.net/product.png");
		assertThat(document.selectFirst("meta[property=kakao:commerce:brand_name]").attr("content"))
			.isEqualTo("나이키");
	}

	@Test
	void buildsAblyProductMetadataHtmlFromPublicProductApiResponse() {
		String apiBody = """
			{
			  "goods": {
			    "sno": 70247267,
			    "name": "made 엣지 올브러쉬 부츠컷 데님",
			    "cover_images": ["https://d3ha2047wt6x28.cloudfront.net/product.jpg"],
			    "price_info": {
			      "consumer": 65000,
			      "thumbnail_price": 52110
			    },
			    "market": {"name": "홀리"}
			  }
			}
			""";

		String html = JsoupPageFetcher.ablyProductMetadataHtml(apiBody).orElseThrow();
		Document document = Jsoup.parse(html);

		assertThat(document.selectFirst("meta[property=og:title]").attr("content"))
			.isEqualTo("made 엣지 올브러쉬 부츠컷 데님");
		assertThat(document.selectFirst("meta[property=product:price:amount]").attr("content"))
			.isEqualTo("52110");
		assertThat(document.selectFirst("meta[property=product:price:currency]").attr("content"))
			.isEqualTo("KRW");
		assertThat(document.selectFirst("meta[property=og:image]").attr("content"))
			.isEqualTo("https://d3ha2047wt6x28.cloudfront.net/product.jpg");
		assertThat(document.selectFirst("meta[property=kakao:commerce:brand_name]").attr("content"))
			.isEqualTo("홀리");
	}

	@Test
	void rejectsAblyProductApiMetadataWhenCurrentPriceIsZero() {
		String apiBody = """
			{"goods":{"name":"상품","cover_images":["https://example.com/product.jpg"],"price_info":{"thumbnail_price":0}}}
			""";

		assertThat(JsoupPageFetcher.ablyProductMetadataHtml(apiBody)).isEmpty();
	}
}
