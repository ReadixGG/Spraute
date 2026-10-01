import java.nio.file.*;
import org.zonarstudio.spraute_engine.script.*;
import java.util.*;

public class TestParse {
  public static void main(String[] a) throws Exception {
    String broken = Files.readString(Path.of("tools/broken_user_script.spr"));
    try { ScriptLanguageSmoke.compile(broken); System.out.println("BROKEN: compiled OK (unexpected)"); }
    catch (Exception e) { System.out.println("BROKEN: " + e.getMessage()); }
    String fixed = Files.readString(Path.of("tools/test_user_script.spr"));
    try { ScriptLanguageSmoke.compile(fixed); System.out.println("FIXED: compiled OK"); }
    catch (Exception e) { System.out.println("FIXED: " + e.getMessage()); }
  }
}
