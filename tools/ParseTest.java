import org.zonarstudio.spraute_engine.script.*;
import java.nio.file.*;
import java.util.List;

public class ParseTest {
    public static void main(String[] args) throws Exception {
        String src = Files.readString(Path.of(args[0]));
        ScriptLexer lexer = new ScriptLexer(src);
        List<ScriptToken> tokens = lexer.tokenize();
        ScriptParser parser = new ScriptParser(tokens);
        parser.parse();
        System.out.println("OK, nodes parsed");
    }
}
