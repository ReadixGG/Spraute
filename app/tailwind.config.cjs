/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./index.html', './src/**/*.{js,html}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        background: 'var(--color-bg)',
        surface: 'var(--color-surface)',
        'surface-low': 'var(--color-surface-low)',
        'surface-container': 'var(--color-surface-container)',
        'surface-bright': 'var(--color-surface-bright)',
        primary: 'var(--color-primary)',
        secondary: 'var(--color-secondary)',
        tertiary: 'var(--color-tertiary)',
        'on-surface': 'var(--color-on-surface)',
        'on-variant': 'var(--color-on-variant)',
        outline: 'var(--color-outline)',
      },
      fontFamily: {
        headline: ['var(--font-headline)', '"Space Grotesk"', 'system-ui', 'sans-serif'],
        body: ['var(--font-body)', 'Inter', 'system-ui', 'sans-serif'],
        mono: ['var(--font-mono)', '"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
};
