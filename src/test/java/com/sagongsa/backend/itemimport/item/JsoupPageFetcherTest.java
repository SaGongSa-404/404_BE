package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

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
}
