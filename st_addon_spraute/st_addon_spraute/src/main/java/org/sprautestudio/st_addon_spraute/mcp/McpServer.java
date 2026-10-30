package org.sprautestudio.st_addon_spraute.mcp;

import com.google.gson.*;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A Model Context Protocol server inside the game (Streamable HTTP, JSON answers): an AI assistant such
 * as Claude Code connects to http://127.0.0.1:PORT/mcp and works with the editor, the stories and the
 * world through {@link McpTools}. Only this computer can connect (the loopback address).
 */
public final class McpServer {
    private McpServer() {
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static ServerSocket socket;
    private static Thread thread;
    private static int port;
    private static volatile String lastClient = "";
    private static volatile long lastCall;

    public static boolean running() {
        return socket != null && !socket.isClosed();
    }

    public static int port() {
        return port;
    }

    public static String lastClient() {
        return lastClient;
    }

    public static long lastCall() {
        return lastCall;
    }

    public static synchronized boolean start(int p) {
        stop();
        try {
            // IPv4 explicitly: Java may pick ::1 for "loopback", and clients knock on 127.0.0.1
            socket = new ServerSocket(p, 16, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
            port = p;
        } catch (IOException e) {
            St_addon_spraute.LOGGER.warn("Spraute MCP: cannot listen on port {}", p, e);
            socket = null;
            return false;
        }
        ServerSocket s = socket;
        thread = new Thread(() -> {
            while (!s.isClosed()) {
                try {
                    Socket c = s.accept();
                    Thread t = new Thread(() -> serve(c), "Spraute MCP client");
                    t.setDaemon(true);
                    t.start();
                } catch (IOException ignored) {
                }
            }
        }, "Spraute MCP");
        thread.setDaemon(true);
        thread.start();
        St_addon_spraute.LOGGER.info("Spraute MCP: listening on http://127.0.0.1:{}/mcp", p);
        return true;
    }

    public static synchronized void stop() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
    }

    // ================================================================== HTTP

    private static void serve(Socket c) {
        try (c) {
            c.setSoTimeout(120_000);
            InputStream in = new BufferedInputStream(c.getInputStream());
            OutputStream out = c.getOutputStream();
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] rl = requestLine.split(" ");
            String method = rl[0], path = rl.length > 1 ? rl[1] : "/";
            Map<String, String> headers = new HashMap<>();
            String h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                int i = h.indexOf(':');
                if (i > 0) headers.put(h.substring(0, i).trim().toLowerCase(Locale.ROOT), h.substring(i + 1).trim());
            }
            byte[] body = new byte[0];
            String len = headers.get("content-length");
            if (len != null) {
                int n = Integer.parseInt(len.trim());
                body = in.readNBytes(n);
            }
            if (!path.startsWith("/mcp") && !path.equals("/")) {
                respond(out, 404, "text/plain", "Spraute MCP: use /mcp", null);
                return;
            }
            // only programs on this computer, never a web page: a browser always says where it came from
            String origin = headers.getOrDefault("origin", "");
            if (!origin.isEmpty() && !origin.matches("https?://(127\\.0\\.0\\.1|localhost)(:\\d+)?")) {
                respond(out, 403, "text/plain", "Spraute MCP: forbidden origin", null);
                return;
            }
            if (method.equals("POST") && !headers.getOrDefault("content-type", "").contains("application/json")) {
                respond(out, 400, "text/plain", "Spraute MCP: JSON expected", null);
                return;
            }
            switch (method) {
                case "POST" -> handlePost(out, new String(body, StandardCharsets.UTF_8), headers);
                case "GET" -> respond(out, 405, "text/plain", "Spraute MCP: no event stream, use POST", null);
                case "DELETE" -> respond(out, 200, "text/plain", "", null);
                case "OPTIONS" -> respond(out, 204, "text/plain", "", null);
                default -> respond(out, 405, "text/plain", "", null);
            }
        } catch (Exception e) {
            St_addon_spraute.LOGGER.debug("Spraute MCP connection", e);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') break;
            if (ch != '\r') b.write(ch);
        }
        if (ch == -1 && b.size() == 0) return null;
        return b.toString(StandardCharsets.UTF_8);
    }

    private static void respond(OutputStream out, int code, String type, String body, Map<String, String> extra) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(switch (code) {
            case 200 -> "OK";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Error";
        }).append("\r\n");
        sb.append("Content-Type: ").append(type).append("; charset=utf-8\r\n");
        sb.append("Content-Length: ").append(data.length).append("\r\n");
        sb.append("Access-Control-Allow-Origin: http://127.0.0.1\r\n");
        sb.append("Connection: close\r\n");
        if (extra != null) extra.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\r\n"));
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.flush();
    }

    // ================================================================== JSON-RPC

    private static final String SESSION = UUID.randomUUID().toString();

    private static void handlePost(OutputStream out, String body, Map<String, String> headers) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (Exception e) {
            respond(out, 400, "application/json", GSON.toJson(error(JsonNull.INSTANCE, -32700, "Parse error")), null);
            return;
        }
        Map<String, String> extra = Map.of("Mcp-Session-Id", SESSION);
        if (parsed.isJsonArray()) {
            JsonArray answers = new JsonArray();
            for (JsonElement e : parsed.getAsJsonArray()) {
                JsonObject a = handle(e.getAsJsonObject(), headers);
                if (a != null) answers.add(a);
            }
            if (answers.isEmpty()) respond(out, 202, "application/json", "", extra);
            else respond(out, 200, "application/json", GSON.toJson(answers), extra);
            return;
        }
        JsonObject a = handle(parsed.getAsJsonObject(), headers);
        if (a == null) respond(out, 202, "application/json", "", extra);
        else respond(out, 200, "application/json", GSON.toJson(a), extra);
    }

    /** One message; null for notifications (no answer). */
    private static JsonObject handle(JsonObject msg, Map<String, String> headers) {
        JsonElement id = msg.get("id");
        String method = msg.has("method") ? msg.get("method").getAsString() : "";
        JsonObject params = msg.has("params") && msg.get("params").isJsonObject() ? msg.getAsJsonObject("params") : new JsonObject();
        if (id == null || id.isJsonNull()) return null; // a notification or an answer to us
        lastCall = System.currentTimeMillis();
        try {
            return switch (method) {
                case "initialize" -> {
                    JsonObject r = new JsonObject();
                    String ver = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : "2025-03-26";
                    r.addProperty("protocolVersion", ver);
                    JsonObject caps = new JsonObject();
                    JsonObject tools = new JsonObject();
                    tools.addProperty("listChanged", false);
                    caps.add("tools", tools);
                    r.add("capabilities", caps);
                    JsonObject info = new JsonObject();
                    info.addProperty("name", "spraute");
                    info.addProperty("title", "Spraute engine");
                    info.addProperty("version", "0.1");
                    r.add("serverInfo", info);
                    r.addProperty("instructions", McpTools.INSTRUCTIONS);
                    if (params.has("clientInfo") && params.getAsJsonObject("clientInfo").has("name"))
                        lastClient = params.getAsJsonObject("clientInfo").get("name").getAsString();
                    yield result(id, r);
                }
                case "ping" -> result(id, new JsonObject());
                case "tools/list" -> {
                    JsonObject r = new JsonObject();
                    r.add("tools", McpTools.list());
                    yield result(id, r);
                }
                case "tools/call" -> {
                    String name = params.get("name").getAsString();
                    JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject() ? params.getAsJsonObject("arguments") : new JsonObject();
                    yield result(id, McpTools.call(name, args));
                }
                case "resources/list" -> {
                    JsonObject r = new JsonObject();
                    r.add("resources", new JsonArray());
                    yield result(id, r);
                }
                case "prompts/list" -> {
                    JsonObject r = new JsonObject();
                    r.add("prompts", new JsonArray());
                    yield result(id, r);
                }
                default -> error(id, -32601, "Method not found: " + method);
            };
        } catch (Exception e) {
            return error(id, -32603, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static JsonObject result(JsonElement id, JsonObject r) {
        JsonObject o = new JsonObject();
        o.addProperty("jsonrpc", "2.0");
        o.add("id", id);
        o.add("result", r);
        return o;
    }

    private static JsonObject error(JsonElement id, int code, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("jsonrpc", "2.0");
        o.add("id", id);
        JsonObject e = new JsonObject();
        e.addProperty("code", code);
        e.addProperty("message", message);
        o.add("error", e);
        return o;
    }
}
