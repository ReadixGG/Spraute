import org.zonarstudio.spraute_engine.script.*;
import java.nio.file.*;
import java.util.List;

public class CompileTest {
    public static void main(String[] args) throws Exception {
        String src = Files.readString(Path.of(args[0]));
        ScriptLexer lexer = new ScriptLexer(src);
        List<ScriptToken> tokens = lexer.tokenize();
        ScriptParser parser = new ScriptParser(tokens);
        List<ScriptNode> nodes = parser.parse();
        ScriptCompiler compiler = new ScriptCompiler();
        CompiledScript compiled = compiler.compile(Path.of(args[0]).getFileName().toString().replace(".spr", ""), nodes);
        System.out.println("OK, compiled " + compiled.getInstructions().size() + " instructions");
    }
}
