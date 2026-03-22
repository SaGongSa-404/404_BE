import com.fourohfour.backend.modules.content.application.*;
import java.time.LocalDate;
import java.util.List;

public class HeuristicDebugRun {
  public static void main(String[] args) {
    HeuristicActionCardGenerator g = new HeuristicActionCardGenerator();
    var samples = List.of(
      new ActionCardGenerationSource("https://www.youtube.com/watch?v=fit","youtube.com",null,null,List.of(),"Beginner Battle Rope Workout","workout guide","초보자를 위한 배틀 로프 운동 가이드","youtube",null,"YouTube",List.of()),
      new ActionCardGenerationSource("https://www.travelandleisure.com/travel-guide/iceland","travelandleisure.com",null,null,List.of(),"Iceland Travel Guide","Travel Guide","아이슬란드 여행 정보 가이드","generic",null,"Travel + Leisure",List.of()),
      new ActionCardGenerationSource("https://english.visitseoul.net/events/sample","english.visitseoul.net",null,null,List.of(),"Seoul Friendship Festival 2025 – Global Food & Culture","festival guide","festival and culture event","generic",null,"Visit Seoul",List.of())
    );
    for (var s : samples) {
      GeneratedPracticeCard c = g.generate(s, LocalDate.of(2026,3,22));
      System.out.println("TITLE=" + s.effectiveTitle());
      System.out.println("CATEGORY=" + c.category().name());
      System.out.println("ACTION=" + c.actionTitle());
      System.out.println("DETAIL=" + c.actionDetail());
      System.out.println("---");
    }
  }
}
