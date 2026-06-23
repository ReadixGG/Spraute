import org.zonarstudio.spraute_engine.script.*;
import java.nio.file.*;
import java.util.List;

public class TokenDump {
    public static void main(String[] args) throws Exception {
        String src = Files.readString(Path.of(args[0]));
        ScriptLexer lexer = new ScriptLexer(src);
        List<ScriptToken> tokens = lexer.tokenize();
        int n = 0;
        for (ScriptToken t : tokens) {
            if (t.getLine() >= 7 && t.getLine() <= 16) {
                System.out.println(n + " L" + t.getLine() + " " + t.getType() + " '" + t.getValue() + "'");
            }
            n++;
        }
    }
}
