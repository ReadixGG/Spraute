package org.zonarstudio.spraute_engine.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parser that converts a list of tokens into an AST (list of ScriptNodes).
 */
public class ScriptParser {

    private final List<ScriptToken> tokens;
    private int pos;

    public ScriptParser(List<ScriptToken> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    /**
     * Parse all tokens into a list of script nodes.
     */
    
    private <T extends ScriptNode> T withPos(T node, ScriptToken token) {
        if (token != null) {
            node.setLine(token.getLine());
            node.setColumn(token.getColumn());
        }
        return node;
    }

    public List<ScriptNode> parse() {
        List<ScriptNode> nodes = new ArrayList<>();

        while (!isAtEnd()) {
            if (match(ScriptToken.TokenType.NEWLINE)) continue;
            if (check(ScriptToken.TokenType.EOF)) break;

            nodes.add(parseDeclaration());
        }

        return nodes;
    }

    private ScriptNode parseDeclaration() {
        String scope = "local";
        if (match(ScriptToken.TokenType.GLOBAL)) {
            scope = "global";
        } else if (match(ScriptToken.TokenType.WORLD)) {
            scope = "world";
        }
        if (match(ScriptToken.TokenType.VAL) || match(ScriptToken.TokenType.VAR)) {
            if (scope.equals("local")) {
                if (match(ScriptToken.TokenType.GLOBAL)) {
                    scope = "global";
                } else if (match(ScriptToken.TokenType.WORLD)) {
                    scope = "world";
                }
            }
            return parseVariableDeclaration(scope);
        }
        if (!scope.equals("local")) {
            throw new ScriptException("Expected 'val' or 'var' after 'global' or 'world'", pos < tokens.size() ? tokens.get(pos).getLine() : -1);
        }

        if (match(ScriptToken.TokenType.AWAIT)) {
            if (match(ScriptToken.TokenType.TASK)) {
                expect(ScriptToken.TokenType.LPAREN, "Expected '(' after 'task'");
                return withPos(new ScriptNode.AwaitNode(new ScriptNode.FunctionCallNode("task", parseArguments())), previous());
            }
            ScriptNode node = parseStatement();
            if (node instanceof ScriptNode.FunctionCallNode || node instanceof ScriptNode.MethodCallNode) {
                return withPos(new ScriptNode.AwaitNode(node), previous());
            }
            throw new ScriptException("Expected function or method call after 'await'", previous().getLine());
        }

        if (match(ScriptToken.TokenType.WHILE)) {
            return parseWhileStatement();
        }

        if (match(ScriptToken.TokenType.FOR)) {
            return parseForStatement();
        }

        if (match(ScriptToken.TokenType.IF)) {
            return parseIfStatement();
        }

        if (match(ScriptToken.TokenType.FUN)) {
            return parseFunctionDef();
        }

        if (match(ScriptToken.TokenType.RETURN)) {
            return parseReturn();
        }

        if (match(ScriptToken.TokenType.ON)) {
            return parseOnStatement();
        }

        if (match(ScriptToken.TokenType.EVERY)) {
            return parseEveryStatement();
        }

        if (match(ScriptToken.TokenType.STOP)) {
            return parseStopStatement();
        }

        if (match(ScriptToken.TokenType.ASYNC)) {
            return parseAsyncStatement();
        }

        
        if (match(ScriptToken.TokenType.TRY)) {
            return parseTryCatch();
        }

        if (match(ScriptToken.TokenType.INCLUDE)) {
            return parseInclude();
        }

        if (check(ScriptToken.TokenType.IDENTIFIER) && "autorun".equals(tokens.get(pos).getValue())) {
            return parseAutorun();
        }

        // Schema-only: declare section / declare node type / declare inspector { ... }
        if (check(ScriptToken.TokenType.IDENTIFIER) && "declare".equals(tokens.get(pos).getValue())) {
            return skipDeclareStatement();
        }

        if (check(ScriptToken.TokenType.LBRACE)) {
            return parseBlock();
        }

        return parseStatement();
    }

    /** Skip declare section / declare node type / declare inspector (handled by CustomNodeRegistry). */
    private ScriptNode skipDeclareStatement() {
        advance(); // declare
        if (check(ScriptToken.TokenType.IDENTIFIER) && "section".equals(tokens.get(pos).getValue())) {
            advance();
            while (!isAtEnd() && !check(ScriptToken.TokenType.NEWLINE) && !check(ScriptToken.TokenType.EOF)) {
                advance();
            }
            match(ScriptToken.TokenType.NEWLINE);
            return withPos(new ScriptNode.BlockNode(java.util.Collections.emptyList()), previous());
        }
        if (check(ScriptToken.TokenType.IDENTIFIER) && "node".equals(tokens.get(pos).getValue())) {
            advance();
            if (check(ScriptToken.TokenType.IDENTIFIER) && "type".equals(tokens.get(pos).getValue())) advance();
            if (check(ScriptToken.TokenType.IDENTIFIER)) advance(); // type id
            skipDeclareBraceBlock();
            return withPos(new ScriptNode.BlockNode(java.util.Collections.emptyList()), previous());
        }
        if (check(ScriptToken.TokenType.IDENTIFIER) && "inspector".equals(tokens.get(pos).getValue())) {
            advance();
            if (check(ScriptToken.TokenType.IDENTIFIER) || check(ScriptToken.TokenType.STRING)) advance();
            skipDeclareBraceBlock();
            return withPos(new ScriptNode.BlockNode(java.util.Collections.emptyList()), previous());
        }
        // Unknown declare form — consume to end of line
        while (!isAtEnd() && !check(ScriptToken.TokenType.NEWLINE) && !check(ScriptToken.TokenType.EOF)) {
            advance();
        }
        return withPos(new ScriptNode.BlockNode(java.util.Collections.emptyList()), previous());
    }

    private void skipDeclareBraceBlock() {
        skipNewlines();
        if (!check(ScriptToken.TokenType.LBRACE)) return;
        advance();
        int depth = 1;
        while (!isAtEnd() && depth > 0) {
            ScriptToken t = advance();
            if (t.getType() == ScriptToken.TokenType.LBRACE) depth++;
            else if (t.getType() == ScriptToken.TokenType.RBRACE) depth--;
        }
    }

    /**
     * После точки / в create-блоках: имя свойства/метода может быть IDENTIFIER
     * или ключевым словом ({@code model}, {@code stop}, {@code npc}, …).
     */
    private String parsePropertyOrMethodName() {
        if (isAtEnd()) {
            throw new ScriptException("Expected property name after '.' (got EOF)", tokens.isEmpty() ? 1 : tokens.get(tokens.size() - 1).getLine());
        }
        ScriptToken tok = advance();
        if (isPropertyNameToken(tok.getType())) {
            return tok.getValue();
        }
        throw new ScriptException("Expected property name after '.' (got " + tok + ")", tok.getLine());
    }

    /** Ключевые слова, допустимые как имена свойств в create-блоках и после точки. */
    private static boolean isPropertyNameToken(ScriptToken.TokenType type) {
        return type == ScriptToken.TokenType.IDENTIFIER
                || type == ScriptToken.TokenType.STOP
                || type == ScriptToken.TokenType.MODEL
                || type == ScriptToken.TokenType.NPC
                || type == ScriptToken.TokenType.UI
                || type == ScriptToken.TokenType.COMMAND
                || type == ScriptToken.TokenType.CAMERA
                || type == ScriptToken.TokenType.WORLD
                || type == ScriptToken.TokenType.GLOBAL
                || type == ScriptToken.TokenType.TASK
                || type == ScriptToken.TokenType.TRUE
                || type == ScriptToken.TokenType.FALSE
                || type == ScriptToken.TokenType.NULL
                || type == ScriptToken.TokenType.ON
                || type == ScriptToken.TokenType.EVERY
                || type == ScriptToken.TokenType.IN;
    }

    /** Static registry creates (skipped by executor); not custom scene nodes. */
    private static boolean isStaticCreateKind(String kind) {
        if (kind == null) return false;
        return switch (kind.toLowerCase()) {
            case "block", "item", "craft", "world", "drop", "tab",
                 "armor", "plugin", "sound", "particles" -> true;
            default -> false;
        };
    }

    private ScriptToken expectPropertyName(String message) {
        if (isAtEnd()) {
            throw new ScriptException(message + " (got EOF)", tokens.isEmpty() ? 1 : tokens.get(tokens.size() - 1).getLine());
        }
        ScriptToken tok = tokens.get(pos);
        if (!isPropertyNameToken(tok.getType())) {
            throw new ScriptException(message + " (got " + tok + ")", tok.getLine());
        }
        return advance();
    }

    private ScriptNode parseVariableDeclaration(String scope) {
        ScriptToken name = expect(ScriptToken.TokenType.IDENTIFIER, "Expected variable name");
        expect(ScriptToken.TokenType.ASSIGN, "Expected '=' after variable name");
        ScriptNode initializer = parseExpression();
        return withPos(new ScriptNode.VariableDeclarationNode(name.getValue(), initializer, scope), previous());
    }

    private ScriptNode parseStatement() {
        if (match(ScriptToken.TokenType.CREATE)) {
            if (match(ScriptToken.TokenType.UI)) {
                ScriptToken uiName = expect(ScriptToken.TokenType.IDENTIFIER, "Expected name after 'create ui'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' after create ui name");
                return parseUiBlock(uiName.getValue());
            }
            if (match(ScriptToken.TokenType.COMMAND)) {
                ScriptToken cmdName = expect(ScriptToken.TokenType.IDENTIFIER, "Expected name after 'create command'");
                ScriptNode body = parseBlock();
                return withPos(new ScriptNode.CommandDefNode(cmdName.getValue(), body), previous());
            }
            if (match(ScriptToken.TokenType.NPC)) {
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected NPC identifier after 'create npc'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start NPC block");
                return parseNpcBlock(identifier.getValue());
            }
            if (match(ScriptToken.TokenType.MODEL)) {
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected model identifier after 'create model'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start model block");
                return parseModelBlock(identifier.getValue());
            }
            if (check(ScriptToken.TokenType.IDENTIFIER) && pos < tokens.size() && "hitbox".equals(tokens.get(pos).getValue())) {
                advance();
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected identifier after 'create hitbox'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start hitbox block");
                return parseHitboxBlock(identifier.getValue());
            }
            if (check(ScriptToken.TokenType.IDENTIFIER) && pos < tokens.size() && "empty".equals(tokens.get(pos).getValue())) {
                advance();
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected identifier after 'create empty'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start empty block");
                return parseEmptyBlock(identifier.getValue());
            }
            if (check(ScriptToken.TokenType.IDENTIFIER) && pos < tokens.size()
                    && ("skin_overlay".equals(tokens.get(pos).getValue())
                    || "skinOverlay".equals(tokens.get(pos).getValue()))) {
                advance();
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected identifier after 'create skin_overlay'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start skin_overlay block");
                return parseSkinOverlayBlock(identifier.getValue());
            }
            if (check(ScriptToken.TokenType.IDENTIFIER) && pos < tokens.size()
                    && ("particleEmitter".equals(tokens.get(pos).getValue())
                    || "particle_emitter".equals(tokens.get(pos).getValue())
                    || "particleSystem".equals(tokens.get(pos).getValue())
                    || "particle_system".equals(tokens.get(pos).getValue()))) {
                advance();
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected identifier after 'create particleEmitter'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start particleEmitter block");
                return parseParticleEmitterBlock(identifier.getValue());
            }
            if (check(ScriptToken.TokenType.IDENTIFIER) && pos < tokens.size() && "particle".equals(tokens.get(pos).getValue())) {
                advance();
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected identifier after 'create particle'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start particle block");
                return parseParticleDefBlock(identifier.getValue());
            }
            if (check(ScriptToken.TokenType.IDENTIFIER) && pos < tokens.size() && "projectile".equals(tokens.get(pos).getValue())) {
                advance();
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected identifier after 'create projectile'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start projectile block");
                return parseProjectileDefBlock(identifier.getValue());
            }
            if (check(ScriptToken.TokenType.IDENTIFIER) && pos < tokens.size() && "light".equals(tokens.get(pos).getValue())) {
                advance();
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected identifier after 'create light'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start light block");
                return parseLightBlock(identifier.getValue());
            }
            if (check(ScriptToken.TokenType.IDENTIFIER) && pos < tokens.size()
                    && ("audioSource".equals(tokens.get(pos).getValue())
                    || "audio_source".equals(tokens.get(pos).getValue())
                    || "audio".equals(tokens.get(pos).getValue()))) {
                advance();
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected identifier after 'create audioSource'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start audioSource block");
                return parseAudioSourceBlock(identifier.getValue());
            }
            if (check(ScriptToken.TokenType.IDENTIFIER) && pos < tokens.size()
                    && ("postProcess".equals(tokens.get(pos).getValue())
                    || "postprocess".equals(tokens.get(pos).getValue())
                    || "post_process".equals(tokens.get(pos).getValue())
                    || "postVolume".equals(tokens.get(pos).getValue())
                    || "post_volume".equals(tokens.get(pos).getValue()))) {
                advance();
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected identifier after 'create postProcess'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start postProcess block");
                return parsePostProcessBlock(identifier.getValue());
            }
            if (check(ScriptToken.TokenType.IDENTIFIER) && pos < tokens.size() && "npc_prefab".equals(tokens.get(pos).getValue())) {
                advance();
                ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected prefab id after 'create npc_prefab'");
                expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start npc_prefab block");
                return parseNpcPrefabBlock(identifier.getValue());
            }

            // Skip static data blocks (create block/item/craft/world/..., plugins)
            if (check(ScriptToken.TokenType.IDENTIFIER) || check(ScriptToken.TokenType.WORLD)) {
                String kind = tokens.get(pos).getValue();
                if (isStaticCreateKind(kind) || check(ScriptToken.TokenType.WORLD)) {
                    advance(); // kind: block, item, craft, world, ...
                    if (check(ScriptToken.TokenType.IDENTIFIER)) {
                        advance(); // skip identifier
                        if (check(ScriptToken.TokenType.LBRACE)) {
                            advance();
                            int braceCount = 1;
                            while (!isAtEnd() && braceCount > 0) {
                                ScriptToken t = advance();
                                if (t.getType() == ScriptToken.TokenType.LBRACE) braceCount++;
                                if (t.getType() == ScriptToken.TokenType.RBRACE) braceCount--;
                            }
                            return withPos(new ScriptNode.BlockNode(java.util.Collections.emptyList()), previous());
                        }
                    }
                } else {
                    // Custom scene node: create <typeId> id { … } — type must be declared
                    // in a SCRIPT node of the active scene (validated at spawn).
                    String typeId = kind;
                    advance();
                    ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER,
                            "Expected identifier after 'create " + typeId + "'");
                    expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start " + typeId + " block");
                    return parseCustomNodeBlock(typeId, identifier.getValue());
                }
            }

            throw new ScriptException("Expected 'npc', 'model', 'hitbox', 'empty', 'particleEmitter', 'light', 'audioSource', 'postProcess', 'ui', 'command', custom type, or a static data block after 'create'", previous().getLine());
        }

        if (match(ScriptToken.TokenType.CAMERA)) {
            ScriptToken camId = expect(ScriptToken.TokenType.IDENTIFIER, "Expected camera identifier after 'camera'");
            expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start camera block");
            return parseCameraBlock(camId.getValue());
        }

        int startPos = pos;
        ScriptNode expr = parseExpression();

        if (match(ScriptToken.TokenType.ASSIGN)) {
            ScriptNode value = parseExpression();
            if (expr instanceof ScriptNode.IdentifierNode idNode) {
                return withPos(new ScriptNode.VariableAssignmentNode(idNode.getName(), value), previous());
            } else if (expr instanceof ScriptNode.PropertyAccessNode propNode) {
                return withPos(new ScriptNode.PropertyAssignmentNode(propNode.getObject(), propNode.getPropertyName(), value), previous());
            } else if (expr instanceof ScriptNode.IndexAccessNode indexNode) {
                return withPos(new ScriptNode.IndexAssignmentNode(indexNode.getObject(), indexNode.getIndex(), value), previous());
            } else {
                throw new ScriptException("Invalid assignment target", expr.getLine());
            }
        }

        skipNewlines();
        if (check(ScriptToken.TokenType.LBRACE) && expr instanceof ScriptNode.FunctionCallNode fcn) {
            advance();
            return parseWidgetBody(fcn.getFunctionName(), fcn.getArgs());
        }
        if (check(ScriptToken.TokenType.LBRACE) && expr instanceof ScriptNode.IdentifierNode idNode) {
            advance();
            return parseWidgetBody(idNode.getName(), java.util.Collections.emptyList());
        }

        return expr;
    }

    private ScriptNode parseExpression() {
        return parseOrExpression();
    }

    private ScriptNode parseOrExpression() {
        ScriptNode left = parseAndExpression();

        while (check(ScriptToken.TokenType.OR)) {
            ScriptToken operator = advance();
            ScriptNode right = parseAndExpression();
            left = new ScriptNode.BinaryExpressionNode(left, operator, right);
        }

        return left;
    }

    private ScriptNode parseAndExpression() {
        ScriptNode left = parseRelationalExpression();

        while (check(ScriptToken.TokenType.AND)) {
            ScriptToken operator = advance();
            ScriptNode right = parseRelationalExpression();
            left = new ScriptNode.BinaryExpressionNode(left, operator, right);
        }

        return left;
    }

    private ScriptNode parseRelationalExpression() {
        ScriptNode left = parseBinaryExpression();

        while (check(ScriptToken.TokenType.EQ) || check(ScriptToken.TokenType.NEQ) ||
               check(ScriptToken.TokenType.GT) || check(ScriptToken.TokenType.LT) ||
               check(ScriptToken.TokenType.GTE) || check(ScriptToken.TokenType.LTE)) {
            ScriptToken operator = advance();
            ScriptNode right = parseBinaryExpression();
            left = new ScriptNode.BinaryExpressionNode(left, operator, right);
        }

        return left;
    }

    private ScriptNode parseBinaryExpression() {
        return parseAdditive();
    }

    private ScriptNode parseAdditive() {
        ScriptNode left = parseMultiplicative();

        while (check(ScriptToken.TokenType.PLUS) || check(ScriptToken.TokenType.MINUS)) {
            ScriptToken operator = advance();
            ScriptNode right = parseMultiplicative();
            left = new ScriptNode.BinaryExpressionNode(left, operator, right);
        }

        return left;
    }

    private ScriptNode parseMultiplicative() {
        ScriptNode left = parseUnary();

        while (check(ScriptToken.TokenType.STAR) || check(ScriptToken.TokenType.SLASH) ||
               check(ScriptToken.TokenType.SLASH_SLASH) || check(ScriptToken.TokenType.PERCENT) ||
               check(ScriptToken.TokenType.STAR_STAR)) {
            ScriptToken operator = advance();
            ScriptNode right = parseUnary();
            left = new ScriptNode.BinaryExpressionNode(left, operator, right);
        }

        return left;
    }

    private ScriptNode parseUnary() {
        if (match(ScriptToken.TokenType.NOT)) {
            ScriptNode operand = parseUnary();
            return withPos(new ScriptNode.UnaryNotNode(operand), previous());
        }
        if (match(ScriptToken.TokenType.MINUS)) {
            ScriptNode operand = parseUnary();
            return withPos(new ScriptNode.UnaryMinusNode(operand), previous());
        }
        return parsePostfix();
    }

    private ScriptNode parsePostfix() {
        ScriptNode expr = parsePrimary();
        
        while (true) {
            if (match(ScriptToken.TokenType.LBRACKET)) {
                ScriptNode index = parseExpression();
                expect(ScriptToken.TokenType.RBRACKET, "Expected ']' after index");
                expr = new ScriptNode.IndexAccessNode(expr, index);
            } else if (match(ScriptToken.TokenType.DOT)) {
                String member = parsePropertyOrMethodName();
                if (match(ScriptToken.TokenType.LPAREN)) {
                    expr = withPos(new ScriptNode.MethodCallNode(expr, member, parseArguments()), previous());
                } else {
                    expr = withPos(new ScriptNode.PropertyAccessNode(expr, member), previous());
                }
            } else {
                break;
            }
        }
        
        return expr;
    }

    private ScriptNode parseNpcBlock(String identifier) {
        return withPos(new ScriptNode.NpcBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseModelBlock(String identifier) {
        return withPos(new ScriptNode.ModelBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseHitboxBlock(String identifier) {
        return withPos(new ScriptNode.HitboxBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseEmptyBlock(String identifier) {
        return withPos(new ScriptNode.EmptyBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseSkinOverlayBlock(String identifier) {
        return withPos(new ScriptNode.SkinOverlayBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseParticleEmitterBlock(String identifier) {
        return withPos(new ScriptNode.ParticleEmitterBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseParticleDefBlock(String identifier) {
        return withPos(new ScriptNode.ParticleDefBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseProjectileDefBlock(String identifier) {
        return withPos(new ScriptNode.ProjectileDefBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseLightBlock(String identifier) {
        return withPos(new ScriptNode.LightBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseAudioSourceBlock(String identifier) {
        return withPos(new ScriptNode.AudioSourceBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parsePostProcessBlock(String identifier) {
        return withPos(new ScriptNode.PostProcessBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseCustomNodeBlock(String typeId, String identifier) {
        return withPos(new ScriptNode.CustomNodeBlockNode(typeId, identifier, parseCreateBlockProps()), previous());
    }

    private ScriptNode parseNpcPrefabBlock(String identifier) {
        return withPos(new ScriptNode.NpcPrefabBlockNode(identifier, parseCreateBlockProps()), previous());
    }

    private java.util.Map<String, List<ScriptNode>> parseCreateBlockProps() {
        java.util.Map<String, List<ScriptNode>> props = new java.util.HashMap<>();
        while (!check(ScriptToken.TokenType.RBRACE) && !isAtEnd()) {
            if (match(ScriptToken.TokenType.NEWLINE)) continue;

            ScriptToken propName = expectPropertyName("Expected property name");
            expect(ScriptToken.TokenType.ASSIGN, "Expected '='");

            List<ScriptNode> values = new ArrayList<>();
            do {
                values.add(parseExpression());
            } while (match(ScriptToken.TokenType.COMMA));

            props.put(propName.getValue(), values);
            match(ScriptToken.TokenType.NEWLINE);
        }
        expect(ScriptToken.TokenType.RBRACE, "Expected '}'");
        return props;
    }

    private ScriptNode parseCameraBlock(String identifier) {
        java.util.Map<String, List<ScriptNode>> props = new java.util.HashMap<>();

        while (!check(ScriptToken.TokenType.RBRACE) && !isAtEnd()) {
            if (match(ScriptToken.TokenType.NEWLINE)) continue;

            ScriptToken propName = expect(ScriptToken.TokenType.IDENTIFIER, "Expected property name");
            expect(ScriptToken.TokenType.ASSIGN, "Expected '='");

            List<ScriptNode> values = new ArrayList<>();
            do {
                values.add(parseExpression());
            } while (match(ScriptToken.TokenType.COMMA));

            props.put(propName.getValue(), values);

            if (!match(ScriptToken.TokenType.NEWLINE) && !check(ScriptToken.TokenType.RBRACE)) {
                // optionally expect statement terminator
            }
        }

        expect(ScriptToken.TokenType.RBRACE, "Expected '}'");
        return withPos(new ScriptNode.CameraBlockNode(identifier, props), previous());
    }

    private List<ScriptNode> parseArguments() {
        List<ScriptNode> args = new ArrayList<>();
        skipNewlines();
        if (!check(ScriptToken.TokenType.RPAREN)) {
            do {
                skipNewlines();
                args.add(parseExpression());
            } while (match(ScriptToken.TokenType.COMMA));
        }
        skipNewlines();
        expect(ScriptToken.TokenType.RPAREN, "Expected ')' after arguments");
        return args;
    }

    private ScriptNode parseBlock() {
        expect(ScriptToken.TokenType.LBRACE, "Expected '{'");
        List<ScriptNode> statements = new ArrayList<>();
        while (!check(ScriptToken.TokenType.RBRACE) && !isAtEnd()) {
            if (match(ScriptToken.TokenType.NEWLINE)) continue;
            statements.add(parseDeclaration());
        }
        expect(ScriptToken.TokenType.RBRACE, "Expected '}'");
        return withPos(new ScriptNode.BlockNode(statements), previous());
    }

    private ScriptNode parseWhileStatement() {
        expect(ScriptToken.TokenType.LPAREN, "Expected '(' after 'while'");
        ScriptNode condition = parseExpression();
        expect(ScriptToken.TokenType.RPAREN, "Expected ')' after while condition");
        skipNewlines();
        
        ScriptNode body;
        if (check(ScriptToken.TokenType.LBRACE)) {
            body = parseBlock();
        } else {
            body = parseDeclaration();
        }
        
        return withPos(new ScriptNode.WhileNode(condition, body), previous());
    }

    private ScriptNode parseIfStatement() {
        List<ScriptNode> conditions = new ArrayList<>();
        List<ScriptNode> bodies = new ArrayList<>();

        expect(ScriptToken.TokenType.LPAREN, "Expected '(' after 'if'");
        conditions.add(parseExpression());
        expect(ScriptToken.TokenType.RPAREN, "Expected ')' after if condition");
        skipNewlines();

        if (check(ScriptToken.TokenType.LBRACE)) {
            bodies.add(parseBlock());
        } else {
            bodies.add(parseDeclaration());
        }

        while (checkElse()) {
            match(ScriptToken.TokenType.ELSE);
            if (match(ScriptToken.TokenType.IF)) {
                expect(ScriptToken.TokenType.LPAREN, "Expected '(' after 'else if'");
                conditions.add(parseExpression());
                expect(ScriptToken.TokenType.RPAREN, "Expected ')' after else if condition");
                skipNewlines();
                if (check(ScriptToken.TokenType.LBRACE)) {
                    bodies.add(parseBlock());
                } else {
                    bodies.add(parseDeclaration());
                }
            } else {
                conditions.add(null); // else branch has no condition
                skipNewlines();
                if (check(ScriptToken.TokenType.LBRACE)) {
                    bodies.add(parseBlock());
                } else {
                    bodies.add(parseDeclaration());
                }
                break; // else is always last
            }
        }

        return withPos(new ScriptNode.IfNode(conditions, bodies), previous());
    }

    /**
     * Check if next non-newline token is 'else'. Handles newlines between } and else.
     */
    private boolean checkElse() {
        int lookahead = pos;
        while (lookahead < tokens.size() && tokens.get(lookahead).getType() == ScriptToken.TokenType.NEWLINE) {
            lookahead++;
        }
        if (lookahead < tokens.size() && tokens.get(lookahead).getType() == ScriptToken.TokenType.ELSE) {
            pos = lookahead; // skip newlines
            return true;
        }
        return false;
    }

    private ScriptNode parseForStatement() {
        expect(ScriptToken.TokenType.LPAREN, "Expected '(' after 'for'");
        ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected variable name in for loop");
        expect(ScriptToken.TokenType.IN, "Expected 'in' after for loop variable");
        ScriptNode iterable = parseExpression();
        expect(ScriptToken.TokenType.RPAREN, "Expected ')' after for loop iterable");
        skipNewlines();
        
        ScriptNode body;
        if (check(ScriptToken.TokenType.LBRACE)) {
            body = parseBlock();
        } else {
            body = parseDeclaration();
        }
        
        return withPos(new ScriptNode.ForNode(identifier.getValue(), iterable, body), previous());
    }

    private ScriptNode parseFunctionDef() {
        ScriptToken name = expect(ScriptToken.TokenType.IDENTIFIER, "Expected function name after 'fun'");
        expect(ScriptToken.TokenType.LPAREN, "Expected '(' after function name");

        List<String> params = new ArrayList<>();
        if (!check(ScriptToken.TokenType.RPAREN)) {
            do {
                ScriptToken param = expect(ScriptToken.TokenType.IDENTIFIER, "Expected parameter name");
                params.add(param.getValue());
            } while (match(ScriptToken.TokenType.COMMA));
        }
        expect(ScriptToken.TokenType.RPAREN, "Expected ')' after parameters");
        skipNewlines();

        ScriptNode body;
        if (check(ScriptToken.TokenType.LBRACE)) {
            body = parseBlock();
        } else {
            body = parseDeclaration();
        }

        return withPos(new ScriptNode.FunctionDefNode(name.getValue(), params, body), previous());
    }

    private ScriptNode parseReturn() {
        if (check(ScriptToken.TokenType.NEWLINE) || check(ScriptToken.TokenType.RBRACE) || check(ScriptToken.TokenType.EOF)) {
            return withPos(new ScriptNode.ReturnNode(null), previous());
        }
        ScriptNode value = parseExpression();
        return withPos(new ScriptNode.ReturnNode(value), previous());
    }

    private ScriptNode parseOnStatement() {
        // on interact(npc_id) -> handler_id { body }
        ScriptToken eventName = expect(ScriptToken.TokenType.IDENTIFIER, "Expected event name after 'on'");
        expect(ScriptToken.TokenType.LPAREN, "Expected '(' after event name");
        List<ScriptNode> eventArgs = new ArrayList<>();
        if (!check(ScriptToken.TokenType.RPAREN)) {
            do {
                eventArgs.add(parseExpression());
            } while (match(ScriptToken.TokenType.COMMA));
        }
        expect(ScriptToken.TokenType.RPAREN, "Expected ')' after event arguments");

        expect(ScriptToken.TokenType.ARROW, "Expected '->' after event declaration");
        ScriptToken handlerId = expect(ScriptToken.TokenType.IDENTIFIER, "Expected handler ID after '->'");
        skipNewlines();

        ScriptNode body;
        if (check(ScriptToken.TokenType.LBRACE)) {
            body = parseBlock();
        } else {
            body = parseDeclaration();
        }

        return withPos(new ScriptNode.OnNode(eventName.getValue(), eventArgs, handlerId.getValue(), body), previous());
    }

    private ScriptNode parseEveryStatement() {
        // every(seconds) -> handler_id { body }
        expect(ScriptToken.TokenType.LPAREN, "Expected '(' after 'every'");
        ScriptNode interval = parseExpression();
        expect(ScriptToken.TokenType.RPAREN, "Expected ')' after interval");

        expect(ScriptToken.TokenType.ARROW, "Expected '->' after every(...)");
        ScriptToken handlerId = expect(ScriptToken.TokenType.IDENTIFIER, "Expected handler ID after '->'");
        skipNewlines();

        ScriptNode body;
        if (check(ScriptToken.TokenType.LBRACE)) {
            body = parseBlock();
        } else {
            body = parseDeclaration();
        }

        return withPos(new ScriptNode.EveryNode(interval, handlerId.getValue(), body), previous());
    }

    private ScriptNode parseStopStatement() {
        // stop(handler_id)
        expect(ScriptToken.TokenType.LPAREN, "Expected '(' after 'stop'");
        ScriptToken handlerId = expect(ScriptToken.TokenType.IDENTIFIER, "Expected handler ID");
        expect(ScriptToken.TokenType.RPAREN, "Expected ')' after handler ID");
        return withPos(new ScriptNode.StopNode(handlerId.getValue()), previous());
    }

    
    private ScriptNode parseTryCatch() {
        ScriptToken startToken = previous();
        ScriptNode tryBlock = parseBlock();
        String catchVarName = null;
        ScriptNode catchBlock = null;
        if (match(ScriptToken.TokenType.CATCH)) {
            if (match(ScriptToken.TokenType.LPAREN)) {
                catchVarName = expect(ScriptToken.TokenType.IDENTIFIER, "Expected variable name for catch block").getValue();
                expect(ScriptToken.TokenType.RPAREN, "Expected ')' after catch variable");
            }
            catchBlock = parseBlock();
        }
        return withPos(new ScriptNode.TryCatchNode(tryBlock, catchVarName, catchBlock), startToken);
    }

    private ScriptNode parseInclude() {
        boolean hasParen = match(ScriptToken.TokenType.LPAREN);
        if (!check(ScriptToken.TokenType.STRING) && !check(ScriptToken.TokenType.IDENTIFIER)) {
            throw new ScriptException("Expected script name after 'import'", previous().getLine());
        }
        advance();
        String scriptName = previous().getValue();
        if (hasParen) {
            expect(ScriptToken.TokenType.RPAREN, "Expected ')' after script name");
        }
        return withPos(new ScriptNode.IncludeNode(scriptName), previous());
    }

    private ScriptNode parseAutorun() {
        advance(); // autorun
        String scriptName = null;
        if (check(ScriptToken.TokenType.STRING)) {
            advance();
            scriptName = previous().getValue();
        } else if (check(ScriptToken.TokenType.IDENTIFIER)) {
            advance();
            scriptName = previous().getValue();
        }
        return withPos(new ScriptNode.AutorunNode(scriptName), previous());
    }

    private ScriptNode parseAsyncStatement() {
        // async { body } or async taskIdExpr { body }
        ScriptNode taskIdExpr = null;
        skipNewlines();
        if (!check(ScriptToken.TokenType.LBRACE)) {
            taskIdExpr = parseExpression();
        }
        skipNewlines();
        ScriptNode body;
        if (check(ScriptToken.TokenType.LBRACE)) {
            body = parseBlock();
        } else {
            body = parseDeclaration();
        }
        return withPos(new ScriptNode.AsyncNode(taskIdExpr, body), previous());
    }

    private void skipNewlines() {
        while (check(ScriptToken.TokenType.NEWLINE)) {
            advance();
        }
    }

    private boolean match(ScriptToken.TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private ScriptToken previous() {
        return tokens.get(pos - 1);
    }

    private ScriptNode parsePrimary() {
        if (match(ScriptToken.TokenType.NUMBER)) {
            String val = previous().getValue();
            if (val.startsWith("0x") || val.startsWith("0X")) {
                return withPos(new ScriptNode.LiteralNode(
                        (int) (Long.parseLong(val.substring(2), 16) & 0xFFFFFFL)), previous());
            }
            if (val.contains(".")) return withPos(new ScriptNode.LiteralNode(Double.parseDouble(val)), previous());
            return withPos(new ScriptNode.LiteralNode(Integer.parseInt(val)), previous());
        }

        if (match(ScriptToken.TokenType.STRING)) {
            return withPos(new ScriptNode.LiteralNode(previous().getValue()), previous());
        }

        if (match(ScriptToken.TokenType.TRUE)) {
            return withPos(new ScriptNode.LiteralNode(true), previous());
        }

        if (match(ScriptToken.TokenType.FALSE)) {
            return withPos(new ScriptNode.LiteralNode(false), previous());
        }

        if (match(ScriptToken.TokenType.NULL)) {
            return withPos(new ScriptNode.LiteralNode(null), previous());
        }

        if (match(ScriptToken.TokenType.AWAIT)) {
            if (match(ScriptToken.TokenType.TASK)) {
                expect(ScriptToken.TokenType.LPAREN, "Expected '(' after 'task'");
                return withPos(new ScriptNode.AwaitNode(new ScriptNode.FunctionCallNode("task", parseArguments())), previous());
            }
            if (match(ScriptToken.TokenType.IDENTIFIER)) {
                String functionName = previous().getValue();
                expect(ScriptToken.TokenType.LPAREN, "Expected '(' after function name");
                return withPos(new ScriptNode.AwaitNode(new ScriptNode.FunctionCallNode(functionName, parseArguments())), previous());
            }
            throw new ScriptException("Expected function call after 'await'", previous().getLine());
        }

        if (match(ScriptToken.TokenType.IDENTIFIER)) {
            String name = previous().getValue();
            
            if (match(ScriptToken.TokenType.LPAREN)) {
                return withPos(new ScriptNode.FunctionCallNode(name, parseArguments()), previous());
            }

            return withPos(new ScriptNode.IdentifierNode(name), previous());
        }

        if (match(ScriptToken.TokenType.LPAREN)) {
            ScriptNode expr = parseExpression();
            expect(ScriptToken.TokenType.RPAREN, "Expected ')' after expression");
            return expr;
        }

        if (match(ScriptToken.TokenType.LBRACKET)) {
            List<ScriptNode> elements = new ArrayList<>();
            skipNewlines();
            if (!check(ScriptToken.TokenType.RBRACKET)) {
                do {
                    skipNewlines();
                    elements.add(parseExpression());
                } while (match(ScriptToken.TokenType.COMMA));
            }
            skipNewlines();
            expect(ScriptToken.TokenType.RBRACKET, "Expected ']' to close list literal");
            return withPos(new ScriptNode.ListLiteralNode(elements), previous());
        }

        if (match(ScriptToken.TokenType.LBRACE)) {
            java.util.Map<String, ScriptNode> entries = new java.util.LinkedHashMap<>();
            skipNewlines();
            while (!check(ScriptToken.TokenType.RBRACE) && !isAtEnd()) {
                if (match(ScriptToken.TokenType.NEWLINE)) continue;
                ScriptToken key = expect(ScriptToken.TokenType.IDENTIFIER, "Expected key in map literal");
                expect(ScriptToken.TokenType.ASSIGN, "Expected '=' after map key");
                entries.put(key.getValue(), parseExpression());
                match(ScriptToken.TokenType.COMMA);
                skipNewlines();
            }
            expect(ScriptToken.TokenType.RBRACE, "Expected '}' to close map literal");
            return withPos(new ScriptNode.MapLiteralNode(entries), previous());
        }

        throw new ScriptException("Expected expression, found " + (pos < tokens.size() ? tokens.get(pos).getType() : "EOF"), pos < tokens.size() ? tokens.get(pos).getLine() : -1);
    }

    private ScriptNode parseUiBlock(String variableName) {
        java.util.Map<String, ScriptNode> rootProps = new java.util.LinkedHashMap<>();
        List<ScriptNode> bodyStatements = new ArrayList<>();

        while (!check(ScriptToken.TokenType.RBRACE) && !isAtEnd()) {
            if (match(ScriptToken.TokenType.NEWLINE)) continue;

            // Try to parse root props (size, background, bg, id) or widget declarations or regular statements
            if (check(ScriptToken.TokenType.WHILE) || check(ScriptToken.TokenType.FOR) ||
                check(ScriptToken.TokenType.IF) || check(ScriptToken.TokenType.VAL) ||
                check(ScriptToken.TokenType.VAR) || check(ScriptToken.TokenType.GLOBAL) ||
                check(ScriptToken.TokenType.WORLD) || check(ScriptToken.TokenType.FUN) ||
                check(ScriptToken.TokenType.RETURN)) {
                bodyStatements.add(parseDeclaration());
                continue;
            }

            ScriptToken idTok = expect(ScriptToken.TokenType.IDENTIFIER, "Expected property name, widget, or statement in create ui block");

            if (match(ScriptToken.TokenType.LPAREN)) {
                // Could be a widget declaration (text, button, scroll, etc.) with { } body,
                // or a regular function call
                List<ScriptNode> wargs = parseArguments();
                skipNewlines();
                if (check(ScriptToken.TokenType.LBRACE)) {
                    advance(); // consume '{'
                    bodyStatements.add(parseWidgetBody(idTok.getValue(), wargs));
                } else {
                    // Regular function call statement (e.g. chat(...))
                    bodyStatements.add(new ScriptNode.FunctionCallNode(idTok.getValue(), wargs));
                }
            } else if (match(ScriptToken.TokenType.ASSIGN)) {
                // Could be root prop or variable assignment
                String name = idTok.getValue();
                ScriptNode value = parseExpression();
                if (name.equals("size") || name.equals("background") || name.equals("bg") || name.equals("id") ||
                    name.equals("canClose") || name.equals("can_close") || name.equals("pos") ||
                    name.equals("dimBackground") || name.equals("dim_background") ||
                    name.equals("radius") || name.equals("gradient") || name.equals("gradientDir") || name.equals("gradient_dir") ||
                    name.equals("border") || name.equals("borderColor") || name.equals("border_color") ||
                    name.equals("borderGradient") || name.equals("borderGradientDir") ||
                    name.equals("outerBorder") || name.equals("outer_border") ||
                    name.equals("outerBorderColor") || name.equals("outer_border_color") ||
                    name.equals("outerBorderGradient") || name.equals("outerBorderGradientDir") ||
                    name.equals("shadow")) {
                    rootProps.put(name, value);
                } else {
                    bodyStatements.add(new ScriptNode.VariableAssignmentNode(name, value));
                }
            } else if (match(ScriptToken.TokenType.DOT)) {
                String propertyName = parsePropertyOrMethodName();
                if (match(ScriptToken.TokenType.LPAREN)) {
                    bodyStatements.add(new ScriptNode.MethodCallNode(new ScriptNode.IdentifierNode(idTok.getValue()), propertyName, parseArguments()));
                } else {
                    expect(ScriptToken.TokenType.ASSIGN, "Expected '=' after property name");
                    ScriptNode value = parseExpression();
                    bodyStatements.add(new ScriptNode.PropertyAssignmentNode(new ScriptNode.IdentifierNode(idTok.getValue()), propertyName, value));
                }
            } else {
                throw new ScriptException("Expected '(', '=', or '.' after '" + idTok.getValue() + "' in create ui block", idTok.getLine());
            }
        }
        expect(ScriptToken.TokenType.RBRACE, "Expected '}' to close create ui block");
        return withPos(new ScriptNode.UiBlockNode(variableName, rootProps, bodyStatements), previous());
    }

    /** Property names valid inside widget { } blocks (anything else with `name = expr` is a variable assignment). */
    private static final Set<String> WIDGET_PROPERTY_NAMES = Set.of(
            "pos", "size", "x", "y", "w", "h", "color", "alpha", "scale", "wrap", "align", "layer",
            "autoWrap", "auto_wrap", "wrapMode", "wrap_mode",
            "mode", "flow", "hug", "hugContent", "hug_content", "stretch", "stretchCells", "stretch_cells",
            "columns", "gridColumns", "cellW", "cellw", "cellH", "cellh", "gapX", "gapx", "gapY", "gapy",
            "gridAlign", "grid_align", "childAlign", "child_align", "alignChildren", "align_children",
            "contentH", "content_h", "scrollbar", "autoScrollbar", "hover", "texture", "source", "svg", "id",
            "playerEnabled", "player_enabled", "enabled", "playing", "autoplay", "loop",
            "stroke", "strokeWidth", "stroke_width", "recolor",
            "slice_borders", "slice_scale", "sliceBorders", "sliceScale",
            "srcU", "srcV", "srcW", "srcH", "src_u", "src_v", "src_w", "src_h",
            "fill", "fillMode", "fill_mode", "fillAmount", "fill_amount",
            "fillOrigin", "fill_origin",
            "cropShape", "crop_shape", "srcShape", "src_shape",
            "feather", "cropFeather", "crop_feather", "edgeBlur", "edge_blur",
            "featherL", "featherLeft", "feather_l", "feather_left",
            "featherT", "featherTop", "feather_t", "feather_top",
            "featherR", "featherRight", "feather_r", "feather_right",
            "featherB", "featherBottom", "feather_b", "feather_bottom",
            "featherSmooth", "feather_smooth",
            "group", "payload", "axis", "constrain", "revert", "threshold",
            "feetCrop", "feet_crop", "crop", "anchor", "anchorX", "anchor_x", "anchorY", "anchor_y", "viewport", "tooltip", "block", "item",
            "labelWrap", "labelScale", "subLabel", "subScale", "bgColor", "outlineColor", "maxLines", "max_lines", "maxChars", "max_chars", "inputType", "placeholder", "text", "gridType", "cellSize", "thickness", "src",
            "effects", "textEffects", "text_effects", "textGradient", "text_gradient",
            "textOutlineColor", "text_outline_color", "textGlowColor", "text_glow_color",
            "effectSpeed", "effect_speed", "effectAmount", "effect_amount",
            "textGlowSize", "text_glow_size", "glowSize", "glow_size",
            "textOutlineWidth", "text_outline_width", "outlineWidth", "outline_width",
            "textUnderlineColor", "text_underline_color", "underlineColor", "underline_color",
            "textUnderlineThickness", "text_underline_thickness", "underlineThickness", "underline_thickness",
            "textUnderlineOffset", "text_underline_offset", "underlineOffset", "underline_offset",
            "textShadowColor", "text_shadow_color", "shadowColor", "shadow_color",
            "textShadowOffsetX", "text_shadow_offset_x", "shadowOffsetX", "shadow_offset_x",
            "textShadowOffsetY", "text_shadow_offset_y", "shadowOffsetY", "shadow_offset_y",
            "textShadowSize", "text_shadow_size",
            "bold", "italic", "underline", "strikethrough", "code", "textShadow",
            "outline", "glow", "rainbow", "pulse", "wave", "shake", "wiggle",
            "glow_pixel", "glow_soft", "outline_pixel", "outline_soft", "shadow_pixel", "shadow_soft",
            "nameTag", "name_tag", "noLookAt", "no_look_at", "noFollowCursor", "no_follow_cursor", "noHurtAnim", "no_hurt_anim", "animation", "renderBones", "render_bones", "skinPlayer", "skin_player",
            "modelGeo", "model_geo", "modelTexture", "model_texture", "modelAnim", "model_anim", "modelIdle", "model_idle",
            "clip", "clipEntity",
            "radius", "gradient", "gradientDir", "gradient_dir", "border", "borderColor", "border_color", "shadow",
            "borderGradient", "borderGradientDir",
            "outerBorder", "outer_border", "outerBorderColor", "outer_border_color",
            "outerBorderGradient", "outerBorderGradientDir",
            "rotation", "pivot", "pivotX", "pivot_x", "pivotY", "pivot_y",
            "yaw", "pitch", "time"
    );

    private static boolean isWidgetPropertyName(String name) {
        return name != null && WIDGET_PROPERTY_NAMES.contains(name);
    }

    private ScriptNode.UiWidgetNode parseWidgetBody(String kind, List<ScriptNode> args) {
        java.util.Map<String, ScriptNode> props = new java.util.LinkedHashMap<>();
        java.util.Map<String, ScriptNode> events = new java.util.LinkedHashMap<>();
        java.util.List<ScriptNode> children = new ArrayList<>();

        while (!check(ScriptToken.TokenType.RBRACE) && !isAtEnd()) {
            if (match(ScriptToken.TokenType.NEWLINE)) continue;

            if (check(ScriptToken.TokenType.WHILE) || check(ScriptToken.TokenType.FOR) ||
                check(ScriptToken.TokenType.IF) || check(ScriptToken.TokenType.VAL) ||
                check(ScriptToken.TokenType.VAR) || check(ScriptToken.TokenType.GLOBAL) ||
                check(ScriptToken.TokenType.WORLD) || check(ScriptToken.TokenType.FUN) ||
                check(ScriptToken.TokenType.RETURN)) {
                children.add(parseDeclaration());
                continue;
            }

            ScriptToken idTok = expect(ScriptToken.TokenType.IDENTIFIER, "Expected property, widget, or statement in widget block");
            if (match(ScriptToken.TokenType.LPAREN)) {
                List<ScriptNode> wargs = parseArguments();
                skipNewlines();
                if (check(ScriptToken.TokenType.LBRACE)) {
                    advance();
                    children.add(parseWidgetBody(idTok.getValue(), wargs));
                } else {
                    children.add(new ScriptNode.FunctionCallNode(idTok.getValue(), wargs));
                }
            } else if (match(ScriptToken.TokenType.LBRACE)) {
                List<ScriptNode> stmts = new ArrayList<>();
                while (!check(ScriptToken.TokenType.RBRACE) && !isAtEnd()) {
                    if (match(ScriptToken.TokenType.NEWLINE)) continue;
                    stmts.add(parseDeclaration());
                }
                expect(ScriptToken.TokenType.RBRACE, "Expected '}' to close event block");
                events.put(idTok.getValue(), new ScriptNode.BlockNode(stmts));
            } else if (match(ScriptToken.TokenType.ASSIGN)) {
                String propOrVar = idTok.getValue();
                ScriptNode value = parseExpression();
                if (isWidgetPropertyName(propOrVar)) {
                    props.put(propOrVar, value);
                } else {
                    children.add(new ScriptNode.VariableAssignmentNode(propOrVar, value));
                }
            } else if (match(ScriptToken.TokenType.DOT)) {
                String propertyName = parsePropertyOrMethodName();
                if (match(ScriptToken.TokenType.LPAREN)) {
                    children.add(new ScriptNode.MethodCallNode(new ScriptNode.IdentifierNode(idTok.getValue()), propertyName, parseArguments()));
                } else {
                    expect(ScriptToken.TokenType.ASSIGN, "Expected '=' after property name");
                    ScriptNode value = parseExpression();
                    children.add(new ScriptNode.PropertyAssignmentNode(new ScriptNode.IdentifierNode(idTok.getValue()), propertyName, value));
                }
            } else {
                throw new ScriptException("Expected '(', '=', or '{' after '" + idTok.getValue() + "' in widget", idTok.getLine());
            }
        }
        expect(ScriptToken.TokenType.RBRACE, "Expected '}' to close widget block");
        return withPos(new ScriptNode.UiWidgetNode(kind, args, props, events, children), previous());
    }

    // --- Helper methods ---

    private boolean isAtEnd() {
        return pos >= tokens.size() || tokens.get(pos).getType() == ScriptToken.TokenType.EOF;
    }

    private boolean check(ScriptToken.TokenType type) {
        if (pos >= tokens.size()) return false;
        return tokens.get(pos).getType() == type;
    }

    private ScriptToken advance() {
        ScriptToken token = tokens.get(pos);
        pos++;
        return token;
    }

    private ScriptToken expect(ScriptToken.TokenType type, String errorMessage) {
        if (check(type)) {
            return advance();
        }
        int line = pos < tokens.size() ? tokens.get(pos).getLine() : -1;
        throw new ScriptException(errorMessage + " (got " + (pos < tokens.size() ? tokens.get(pos) : "EOF") + ")", line);
    }
}
