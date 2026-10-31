package org.sprautestudio.spraute_engine.ui;

/** Color tokens of the Spraute UI (ARGB). */
public final class Theme {
    private Theme() {}

    // surfaces
    public static final int BG = 0xFF16171D;
    public static final int BG_DEEP = 0xFF101116;
    public static final int PANEL = 0xFF1C1E26;
    public static final int PANEL_ALT = 0xFF21232D;
    public static final int RAISED = 0xFF272A36;
    public static final int RAISED_HOVER = 0xFF30344A;
    public static final int INPUT = 0xFF12131A;
    public static final int BORDER = 0xFF2E3140;
    public static final int BORDER_STRONG = 0xFF3D4157;
    public static final int SHADOW = 0x66000000;
    public static final int OVERLAY_DIM = 0x99000000;

    // text
    public static final int TEXT = 0xFFE3E5EE;
    public static final int TEXT_DIM = 0xFF9BA0B5;
    public static final int TEXT_MUTED = 0xFF686D85;
    public static final int TEXT_ON_ACCENT = 0xFFFFFFFF;

    // accent
    public static final int ACCENT = 0xFF8B6CFF;
    public static final int ACCENT_HOVER = 0xFFA08AFF;
    public static final int ACCENT_DIM = 0x558B6CFF;
    public static final int ACCENT_2 = 0xFF39D0B5;

    // states
    public static final int SUCCESS = 0xFF4CC38A;
    public static final int WARNING = 0xFFF2B84B;
    public static final int ERROR = 0xFFF0607A;
    public static final int INFO = 0xFF5DA9F6;

    public static final int SELECTION = 0x553D6BD8;
    public static final int SELECTION_INACTIVE = 0x333D6BD8;
    public static final int HOVER = 0x14FFFFFF;
    public static final int PRESSED = 0x22FFFFFF;

    // code
    public static final int CODE_BG = 0xFF14151B;
    public static final int CODE_LINE = 0xFF1B1D26;
    public static final int CODE_GUTTER = 0xFF14151B;
    public static final int CODE_GUTTER_TEXT = 0xFF4C5066;
    public static final int CODE_GUTTER_ACTIVE = 0xFFB4B8CC;
    public static final int CODE_CARET = 0xFFFFFFFF;
    public static final int CODE_KEYWORD = 0xFFC792EA;
    public static final int CODE_COMMAND = 0xFF82AAFF;
    public static final int CODE_STRING = 0xFFC3E88D;
    public static final int CODE_INTERP = 0xFFFFCB6B;
    public static final int CODE_NUMBER = 0xFFF78C6C;
    public static final int CODE_COMMENT = 0xFF676E95;
    public static final int CODE_OPERATOR = 0xFF89DDFF;
    public static final int CODE_IDENT = 0xFFD6DAEA;
    public static final int CODE_CHARACTER = 0xFF4FD6BE;
    public static final int CODE_LOCATION = 0xFFFFCB6B;
    public static final int CODE_LABEL = 0xFFFF7EB6;
    public static final int CODE_VARIABLE = 0xFF7DCFFF;
    public static final int CODE_FUNCTION = 0xFFFFD580;
    public static final int CODE_PARAM_KW = 0xFF9AA5CE;
    public static final int CODE_ERROR = 0xFFFF5370;
    public static final int CODE_MATCH = 0x33FFCB6B;
    public static final int CODE_OCCURRENCE = 0x2A7DCFFF;
    public static final int CODE_BRACKET = 0x5589DDFF;

    // node categories
    public static int category(String category) {
        if (category == null) return 0xFF5C6370;
        return switch (category) {
            case "Речь" -> 0xFF5DA9F6;
            case "Ожидание" -> 0xFFF2B84B;
            case "Персонажи" -> 0xFF39D0B5;
            case "Анимации" -> 0xFFFF8F5A;
            case "Функции" -> 0xFFE06C9F;
            case "Интерфейс" -> 0xFF5DA9F6;
            case "Камера" -> 0xFFE0566E;
            case "Эффекты" -> 0xFFFFC94A;
            case "События" -> 0xFFE6A800;
            case "Переменные" -> 0xFFFF8C1A;
            case "Операторы" -> 0xFF4FB04F;
            case "Значения" -> 0xFF3FA2C8;
            case "Игрок и мир" -> 0xFF4CC38A;
            case "Экран" -> 0xFFE57BD8;
            case "Логика" -> 0xFF8B6CFF;
            case "Поток" -> 0xFFF0607A;
            case "Объявления" -> 0xFFB0B7C9;
            default -> 0xFF5C6370;
        };
    }

    public static int alpha(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        return ((int) (base * Math.max(0, Math.min(1, a))) << 24) | (color & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int darker(int color, float f) {
        int r = (int) (((color >> 16) & 0xFF) * f), g = (int) (((color >> 8) & 0xFF) * f), b = (int) ((color & 0xFF) * f);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
