package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class ProductApiMetadataHtmlTest {

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

		String html = ProductApiMetadataHtml.fromBunjang(apiBody).orElseThrow();
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

		String html = ProductApiMetadataHtml.fromKream(apiBody).orElseThrow();
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

		String html = ProductApiMetadataHtml.fromAbly(apiBody).orElseThrow();
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

		assertThat(ProductApiMetadataHtml.fromAbly(apiBody)).isEmpty();
	}

	@Test
	void rejectsAblyProductWithoutAnImage() {
		String apiBody = """
			{"goods":{"name":"상품","price_info":{"thumbnail_price":1000}}}
			""";

		assertThat(ProductApiMetadataHtml.fromAbly(apiBody)).isEmpty();
	}

	@Test
	void keepsKreamTitleWhenPriceAndImageAreMissing() {
		String apiBody = """
			{"release":{"name":" KREAM  Sample "}}
			""";

		Document document = Jsoup.parse(ProductApiMetadataHtml.fromKream(apiBody).orElseThrow());

		assertThat(document.selectFirst("meta[property=og:title]").attr("content")).isEqualTo("KREAM  Sample");
		assertThat(document.select("meta[property=product:price:amount], meta[property=og:image]")).isEmpty();
	}

	@Test
	void rejectsBunjangProductWithOnlyADescription() {
		String apiBody = """
			{"data":{"product":{"description":"상품 설명"}}}
			""";

		assertThat(ProductApiMetadataHtml.fromBunjang(apiBody)).isEmpty();
	}
}
