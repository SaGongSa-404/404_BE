import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.*;

public class ScrapeNaverPreview {
  static String attr(Document doc, String selector, String attribute) {
    Element e = doc.selectFirst(selector);
    return e == null ? null : e.attr(attribute);
  }
  static String text(Document doc, String selector) {
    Element e = doc.selectFirst(selector);
    return e == null ? null : e.text();
  }
  static String firstNonBlank(String... vals) {
    for (String v : vals) if (v != null && !v.isBlank()) return v.trim();
    return null;
  }
  static String norm(String v) {
    return v == null ? null : v.replace('\u00A0',' ').replaceAll("\\s+"," ").trim();
  }
  public static void main(String[] args) throws Exception {
    String url = args[0];
    Document doc = Jsoup.connect(url)
      .userAgent("Mozilla/5.0 (compatible; 404ActionDeckBot/1.0; +https://example.com)")
      .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
      .timeout(15000)
      .get();

    String title = firstNonBlank(
      attr(doc, "meta[property=og:title]", "content"),
      attr(doc, "meta[name=twitter:title]", "content"),
      doc.title()
    );
    String description = firstNonBlank(
      attr(doc, "meta[property=og:description]", "content"),
      attr(doc, "meta[name=description]", "content"),
      attr(doc, "meta[name=twitter:description]", "content")
    );
    String author = firstNonBlank(
      attr(doc, "meta[name=author]", "content"),
      attr(doc, "meta[property=article:author]", "content"),
      text(doc, ".nick, .blog2_series .txt_info")
    );
    String siteName = firstNonBlank(
      attr(doc, "meta[property=og:site_name]", "content"),
      attr(doc, "meta[name=application-name]", "content")
    );

    List<String> selectors = List.of(".se-main-container", "#postViewArea", ".post_view", ".contents_style");
    String chosenSelector = null;
    String chosenText = null;
    for (String selector : selectors) {
      List<String> chunks = new ArrayList<>();
      for (Element e : doc.select(selector)) {
        String candidate = e.text();
        if (candidate != null && !candidate.isBlank()) chunks.add(norm(candidate));
      }
      if (!chunks.isEmpty()) {
        chosenSelector = selector;
        chosenText = String.join("\n", chunks);
        break;
      }
    }

    System.out.println("TITLE=" + norm(title));
    System.out.println("DESCRIPTION=" + norm(description));
    System.out.println("AUTHOR=" + norm(author));
    System.out.println("SITE_NAME=" + norm(siteName));
    System.out.println("CHOSEN_SELECTOR=" + chosenSelector);
    if (chosenText != null) {
      System.out.println("TEXT_LENGTH=" + chosenText.length());
      System.out.println("TEXT_PREVIEW_BEGIN");
      System.out.println(chosenText.substring(0, Math.min(3000, chosenText.length())));
      System.out.println("TEXT_PREVIEW_END");
    }
  }
}
