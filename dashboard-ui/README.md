# ReadyBell Dashboard UI

A modern, responsive web dashboard for the ReadyBell restaurant notification platform with seamless light/dark theme support. Built with React 18, TypeScript 5, Tailwind CSS 3, and Vite.

## Tech Stack

- **Frontend Framework**: React 18
- **Language**: TypeScript 5 (strict mode)
- **Styling**: Tailwind CSS 3 + CSS Variables
- **Build Tool**: Vite 5
- **Icons**: Lucide React
- **Utilities**: clsx, date-fns
- **Package Manager**: npm

## Project Setup

### Prerequisites

- Node.js 18+ and npm 9+
- (Optional) Backend API running on `http://localhost:8080`

### Installation

```bash
# Install dependencies
npm install

# Install dev dependencies
npm install -D tailwindcss postcss autoprefixer typescript eslint
```

### Environment Variables

Create a `.env.local` file (optional):

```env
BACKEND_URL=http://localhost:8080   # Vite dev-proxy target for /api (default)
VITE_PUBLIC_URL=                    # origin put into receipt QR codes when the dashboard runs on localhost
# VITE_API_BASE_URL=                # only when the UI is served from a different origin than the API
```

In dev the browser calls the Vite origin and `/api/*` is proxied to `BACKEND_URL` (no CORS). See `../docs/DEVELOPMENT.md`.

## Development

### Start Dev Server

```bash
npm run dev
```

The application will be available at `http://localhost:5173/`

- **Hot Module Replacement (HMR)** enabled for fast development
- **API Proxy** configured at `/api` → backend
- **CSS Hot Reload** for instant style updates

### Build for Production

```bash
npm run build
```

Outputs optimized bundle to `dist/` directory:
- HTML: 0.54 KB (gzipped)
- CSS: ~2.7 KB (gzipped)
- Main JS: ~2.2 KB (gzipped)
- Vendor JS: ~45 KB (gzipped)
- **Total**: < 50 KB (gzipped)

### Type Checking

```bash
# Check for TypeScript errors (strict mode enabled)
npx tsc --noEmit
```

### Linting

```bash
npm run lint
```

### Preview Production Build

```bash
npm run preview
```

## Project Structure

```
dashboard-ui/
├── src/
│   ├── components/          # Reusable UI components
│   ├── context/            # React Context (Theme, etc.)
│   ├── hooks/              # Custom React hooks
│   ├── pages/              # Page components
│   ├── types/              # TypeScript type definitions
│   ├── utils/              # Utility functions
│   ├── styles/             # Global styles & design tokens
│   ├── App.tsx             # Root component
│   └── main.tsx            # Application entry point
├── public/                 # Static assets
├── dist/                   # Production build output
├── index.html              # HTML entry point
├── tailwind.config.js      # Tailwind CSS configuration
├── tsconfig.json           # TypeScript configuration
├── vite.config.ts          # Vite configuration
└── postcss.config.js       # PostCSS configuration
```

## Configuration

### TypeScript (`tsconfig.json`)

- **Strict Mode**: Enabled for type safety
- **Path Aliases**: `@/*` → `src/*`
- **Target**: ES2020
- **Module**: ESNext

### Tailwind CSS (`tailwind.config.js`)

- **Dark Mode**: Class-based strategy (`darkMode: 'class'`)
- **Content**: Scans `src/**/*.{js,ts,jsx,tsx}`
- **Custom Colors**: CSS variable-based (light/dark themes)
- **Spacing Scale**: xs (0.25rem) through 2xl (3rem)

### Vite (`vite.config.ts`)

- **API Proxy**: `/api` routes to backend
- **Code Splitting**: Vendor + UI chunks for optimal loading
- **Source Maps**: Enabled for production debugging
- **Absolute Imports**: `@/` prefix support

## Theme System

### Theme Management

The dashboard supports light and dark themes with persistent user preference:

```typescript
import { useTheme } from '@/context/ThemeContext';

function MyComponent() {
  const { theme, toggleTheme, setTheme } = useTheme();
  
  return (
    <button onClick={toggleTheme}>
      Toggle Theme (Current: {theme})
    </button>
  );
}
```

### Storage

- **Key**: `readybell-theme`
- **Valid Values**: `'light'` | `'dark'`
- **Fallback**: System preference (`prefers-color-scheme`)

### Design Tokens (CSS Variables)

All colors defined as CSS custom properties and synchronized between light/dark modes:

```css
/* Light mode (default) */
--color-primary: #0066CC
--text-primary: #1A1A1A
--status-success: #10B981

/* Dark mode ([data-theme="dark"]) */
--color-primary: #60A5FA
--text-primary: #F3F4F6
--status-success: #34D399
```

## API Integration

### Base URL

The dashboard uses Vite's proxy feature to route API calls:

```
Frontend: /api/orders
→ Backend: http://localhost:8080/api/orders   (the /api prefix is kept)
```

Configure the proxy target via `BACKEND_URL` or `vite.config.ts`.

### Endpoints

See [../docs/API_INTEGRATION.md](../docs/API_INTEGRATION.md) for the endpoints the UI uses.

## Performance Metrics

### Build Output

```
dist/index.html              0.54 kB (gzip: 0.33 kB)
dist/assets/index-*.css      9.51 kB (gzip: 2.70 kB)
dist/assets/index-*.js       5.48 kB (gzip: 2.20 kB)
dist/assets/vendor-*.js    140.91 kB (gzip: 45.30 kB)
─────────────────────────────────────────────────
Total (gzipped):          ~50 KB
```

### Target Metrics

- **First Contentful Paint (FCP)**: < 1.5s
- **Largest Contentful Paint (LCP)**: < 2.5s
- **Cumulative Layout Shift (CLS)**: < 0.1
- **Time to Interactive (TTI)**: < 3.5s

## Accessibility

- **WCAG 2.1 Level AA** compliant
- **Semantic HTML**: `<nav>`, `<main>`, `<header>`, `<aside>`
- **Keyboard Navigation**: Full support with Tab order
- **Color Contrast**: ≥ 4.5:1 for text in both themes
- **ARIA Attributes**: Proper labels, live regions, role attributes
- **Motion**: Respects `prefers-reduced-motion` media query

## Browser Support

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+
- Mobile: iOS Safari 14+, Chrome Android (latest)

## Next Steps

### Upcoming Tasks

1. **Task 1.2**: Setup project conventions and utilities
2. **Task 1.3**: Create CSS variables and design tokens (partially done)
3. **Task 1.4**: Setup theme context (DONE)
4. **Task 2**: Build core layout components (Header, Sidebar, Layout)
5. **Task 3**: Implement theme toggle and persistence
6. **Task 4-5**: Order cards and table components

### Development Workflow

```bash
# 1. Start dev server
npm run dev

# 2. Make changes to components/styles
# HMR will automatically reload

# 3. Check types
npx tsc --noEmit

# 4. Lint code
npm run lint

# 5. Build when ready
npm run build

# 6. Preview production build
npm run preview
```

## Common Issues

### Tailwind CSS Not Applied

- Ensure `@tailwind` directives are in `src/index.css`
- Check that `tailwind.config.js` content paths are correct
- Verify CSS import in `App.tsx` or `main.tsx`

### Theme Not Persisting

- Check browser's localStorage is enabled
- Verify `data-theme` attribute is set on `<html>`
- Ensure `useTheme()` is inside `ThemeProvider`

### Build Failing

```bash
# Clear cache and reinstall
rm -rf node_modules package-lock.json
npm install
npm run build
```

## License

Part of the ReadyBell restaurant notification platform.

## Support

For issues or questions, contact the development team.
