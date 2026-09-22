/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Paleta de cabina: negro OLED + acentos de alto contraste.
        cockpit: {
          bg: '#09090b',      // zinc-950
          panel: '#18181b',   // zinc-900
          line: '#27272a',    // zinc-800
          go: '#10b981',      // emerald-500
          info: '#22d3ee',    // cyan-400
          warn: '#f59e0b',    // amber-500
          stop: '#ef4444',    // red-500
        },
      },
      fontSize: {
        // Legible a mas de 1 metro, con sol directo.
        hud: ['2.75rem', { lineHeight: '1', letterSpacing: '-0.02em' }],
        cta: ['1.375rem', { lineHeight: '1.1', letterSpacing: '0.04em' }],
      },
      minHeight: {
        touch: '4rem',   // 64 px :: minimo tactil en movimiento
        touchxl: '4.5rem', // 72 px
      },
      keyframes: {
        pulseRing: {
          '0%': { transform: 'scale(1)', opacity: '0.55' },
          '100%': { transform: 'scale(1.6)', opacity: '0' },
        },
      },
      animation: { pulseRing: 'pulseRing 1.6s ease-out infinite' },
    },
  },
  plugins: [],
};
