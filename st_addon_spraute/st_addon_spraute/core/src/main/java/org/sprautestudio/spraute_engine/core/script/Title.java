package org.sprautestudio.spraute_engine.core.script;

/** Big text in the middle of the screen. */
public record Title(String text, String subtitle, int fadeIn, int stay, int fadeOut) {
    public static Builder builder(String text) {
        return new Builder(text);
    }

    public static final class Builder {
        private final String text;
        private String subtitle = "";
        private int fadeIn = 10, stay = 70, fadeOut = 20;

        private Builder(String text) {
            this.text = text;
        }

        public Builder subtitle(String s) {
            subtitle = s;
            return this;
        }

        public Builder times(int in, int hold, int out) {
            fadeIn = in;
            stay = hold;
            fadeOut = out;
            return this;
        }

        public Title build() {
            return new Title(text, subtitle, fadeIn, stay, fadeOut);
        }
    }
}
