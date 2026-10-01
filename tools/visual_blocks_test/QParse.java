import org.zonarstudio.spraute_engine.client.editor.visual.*;
import java.nio.file.*;
public class Q {
  public static void main(String[] a) throws Exception {
    var defs = VisualBlockDslParser.parse(Files.readString(Path.of("app/bundled_plugins/procode/blocks/quest_create.spr")), "procode");
    var d = defs.get(0);
    System.out.println("body items: " + d.body.size());
    int i=0;
    for (var it : d.body) {
      System.out.println(i++ + " kind=" + it.kind + " conds=" + it.conds.size());
      if (it.tokens != null) for (var t : it.tokens) System.out.println("  token: " + t);
      if (it.text != null) System.out.println("  textlen=" + it.text.length());
    }
    System.out.println("valueInputs=" + d.valueInputNames());
  }
}
