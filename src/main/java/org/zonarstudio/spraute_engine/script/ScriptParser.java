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
            ScriptNode node = parseStatement();
            if (node instanceof ScriptNode.FunctionCallNode callNode) {
                return withPos(new ScriptNode.AwaitNode(callNode), previous());
            }
            throw new ScriptException("Expected function call after 'await'", previous().getLine());
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

        if (check(ScriptToken.TokenType.LBRACE)) {
            return parseBlock();
        }

        return parseStatement();
    }

    /** После точки: имя свойства/метода может быть IDENTIFIER или ключевое слово stop (npc.stop). */
    private String parsePropertyOrMethodName() {
        if (isAtEnd()) {
            throw new ScriptException("Expected property name after '.' (got EOF)", tokens.isEmpty() ? 1 : tokens.get(tokens.size() - 1).getLine());
        }
        ScriptToken tok = advance();
        if (tok.getType() == ScriptToken.TokenType.IDENTIFIER || tok.getType() == ScriptToken.TokenType.STOP) {
            return tok.getValue();
        }
        throw new ScriptException("Expected property name after '.' (got " + tok + ")", tok.getLine());
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
            expect(ScriptToken.TokenType.NPC, "Expected 'npc', 'ui', or 'command' after 'create'");
            ScriptToken identifier = expect(ScriptToken.TokenType.IDENTIFIER, "Expected NPC identifier after 'create npc'");
            expect(ScriptToken.TokenType.LBRACE, "Expected '{' to start NPC block");
            return parseNpcBlock(identifier.getValue());
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
        ScriptNode left = parseUnary();

        while (check(ScriptToken.TokenType.PLUS) || check(ScriptToken.TokenType.MINUS) ||
               check(ScriptToken.TokenType.STAR) || check(ScriptToken.TokenType.SLASH) ||
               check(ScriptToken.TokenType.SLASH_SLASH) ||
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
        return withPos(new ScriptNode.NpcBlockNode(identifier, props), previous());
    }

    private List<ScriptNode> parseArguments() {
        List<ScriptNode> args = new ArrayList<>();
        if (!check(ScriptToken.TokenType.RPAREN)) {
            do {
                args.add(parseExpression());
            } while (match(ScriptToken.TokenType.COMMA));
        }
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

    private ScriptNode parseAsyncStatement() {
        // async { body } or async "task_id" { body }
        String taskId = null;
        if (check(ScriptToken.TokenType.STRING)) {
            advance();
            taskId = previous().getValue();
        }
        skipNewlines();
        ScriptNode body;
        if (check(ScriptToken.TokenType.LBRACE)) {
            body = parseBlock();
        } else {
            body = parseDeclaration();
        }
        return withPos(new ScriptNode.AsyncNode(taskId, body), previous());
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
            if (!check(ScriptToken.TokenType.RBRACKET)) {
                do {
                    elements.add(parseExpression());
                } while (match(ScriptToken.TokenType.COMMA));
            }
            expect(ScriptToken.TokenType.RBRACKET, "Expected ']' to close list literal");
            return withPos(new ScriptNode.ListLiteralNode(elements), previous());
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
                    name.equals("canClose") || name.equals("can_close") || name.equals("pos")) {
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
            "contentH", "content_h", "scrollbar", "autoScrollbar", "hover", "texture", "id", "slice_borders", "slice_scale",
            "feetCrop", "feet_crop", "crop", "anchor", "anchorX", "anchor_x", "anchorY", "anchor_y", "viewport", "tooltip", "block", "item",
            "labelWrap", "labelScale", "subLabel", "subScale", "bgColor", "outlineColor", "maxLines", "max_lines", "maxChars", "max_chars", "inputType", "placeholder", "gridType", "cellSize", "thickness",
            "nameTag", "name_tag", "noLookAt", "no_look_at", "noFollowCursor", "no_follow_cursor", "noHurtAnim", "no_hurt_anim", "animation", "renderBones", "render_bones"
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
