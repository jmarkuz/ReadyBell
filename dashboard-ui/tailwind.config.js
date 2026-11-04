import colors from 'tailwindcss/colors';

/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['"EB Garamond"', 'Waldenburg', '"Times New Roman"', 'serif'],
      },
      borderRadius: {
        lg: '1rem',
      },
      colors: {
        // Warm neutrals replace the cool slate scale
        slate: colors.stone,
        // "blue" is the old brand hue; remap it to the ink scale (no saturated CTA colour)
        blue: {
          50: '#f0efed',
          100: '#e7e5e4',
          200: '#d6d3d1',
          300: '#a8a29e',
          400: '#d6d3d1',
          500: '#44403c',
          600: '#292524',
          700: '#0c0a09',
          800: '#1c1917',
          900: '#1c1917',
        },
        primary: 'var(--color-primary)',
        secondary: 'var(--color-secondary)',
        accent: 'var(--color-accent)',
        surface: 'var(--color-surface)',
        'status-success': 'var(--status-success)',
        'status-warning': 'var(--status-warning)',
        'status-error': 'var(--status-error)',
        'status-info': 'var(--status-info)',
      },
      backgroundColor: {
        DEFAULT: 'var(--color-bg)',
        surface: 'var(--color-surface)',
      },
      textColor: {
        DEFAULT: 'var(--text-primary)',
        secondary: 'var(--text-secondary)',
        muted: 'var(--text-muted)',
      },
      borderColor: {
        DEFAULT: 'var(--color-border)',
      },
      boxShadow: {
        sm: 'var(--shadow-sm)',
        md: 'var(--shadow-md)',
        lg: 'var(--shadow-lg)',
      },
      spacing: {
        xs: '0.25rem',
        sm: '0.5rem',
        md: '1rem',
        lg: '1.5rem',
        xl: '2rem',
        '2xl': '3rem',
      },
    },
  },
  plugins: [],
}
