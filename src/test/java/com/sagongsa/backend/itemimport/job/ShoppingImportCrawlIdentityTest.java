package com.sagongsa.backend.itemimport.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.itemimport.item.ShoppingLinkImportRequest;
import org.junit.jupiter.api.Test;

class ShoppingImportCrawlIdentityTest {

	@Test
	void usesOliveYoungProductNumberWithoutChangingTheRequestUrl() {
		String firstUrl = "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000171427&referrer=one";
		String secondUrl = "https://m.oliveyoung.co.kr/store/goods/getGoodsDetail.do?referrer=two&goodsNo=A000000171427";

		ShoppingImportCrawlIdentity.Identity first = identity(firstUrl);
		ShoppingImportCrawlIdentity.Identity second = identity(secondUrl);

		assertThat(first.site()).isEqualTo("oliveyoung");
		assertThat(first.key()).isEqualTo(second.key());
		assertThat(firstUrl).contains("referrer=one");
	}

	@Test
	void usesKnownPathProductNumbersAcrossTrackingVariants() {
		assertSameProduct(
			"https://www.musinsa.com/products/6632593?pid=one",
			"https://www.musinsa.com/products/6632593?shortlink=two"
		);
		assertSameProduct(
			"https://www.29cm.co.kr/products/3947586?pid=one",
			"https://www.29cm.co.kr/products/3947586?shortlink=two"
		);
		assertSameProduct(
			"https://m.a-bly.com/goods/73715831?foo=one",
			"https://m.a-bly.com/goods/73715831?foo=two"
		);
		assertSameProduct(
			"https://kream.co.kr/products/677299?foo=one",
			"https://www.kream.co.kr/products/677299?foo=two"
		);
		assertSameProduct(
			"https://m.bunjang.co.kr/products/416303366?foo=one",
			"https://m.bunjang.co.kr/products/416303366?foo=two"
		);
		assertSameProduct(
			"https://store.zigzag.kr/catalog/products/143224732?catalog_product_id=143224732",
			"https://store.zigzag.kr/catalog/products/143224732?foo=two"
		);
	}

	@Test
	void fallsBackToTheExactUrlWhenProductNumberIsNotCertain() {
		ShoppingImportCrawlIdentity.Identity first = identity("https://shop.example.com/item/abc?ref=one");
		ShoppingImportCrawlIdentity.Identity second = identity("https://shop.example.com/item/abc?ref=two");

		assertThat(first.site()).isEqualTo("other");
		assertThat(first.key()).isNotEqualTo(second.key());
	}

	@Test
	void fallsBackToTheExactUrlForUnverifiedKnownSiteProductIds() {
		assertThat(identity(
			"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=not-a-product&ref=one"
		).key()).isNotEqualTo(identity(
			"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=not-a-product&ref=two"
		).key());
		assertThat(identity("https://www.musinsa.com/products/not-a-number?ref=one").key())
			.isNotEqualTo(identity("https://www.musinsa.com/products/not-a-number?ref=two").key());
	}

	@Test
	void doesNotCreateCrawlIdentityForDirectInput() {
		ShoppingLinkImportRequest request = new ShoppingLinkImportRequest(
			ItemInputSource.DIRECT_INPUT,
			null,
			"직접 입력 상품",
			null,
			1000,
			null
		);

		assertThat(ShoppingImportCrawlIdentity.from(request)).isEmpty();
	}

	private void assertSameProduct(String firstUrl, String secondUrl) {
		assertThat(identity(firstUrl).key()).isEqualTo(identity(secondUrl).key());
	}

	private ShoppingImportCrawlIdentity.Identity identity(String url) {
		ShoppingLinkImportRequest request = new ShoppingLinkImportRequest(
			ItemInputSource.SHARE,
			url,
			null,
			null,
			null,
			null
		);
		return ShoppingImportCrawlIdentity.from(request).orElseThrow();
	}
}
