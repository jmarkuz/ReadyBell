/**
 * Color Contrast Utility Functions
 * 
 * WCAG 2.1 AA Compliance:
 * - Normal text: ≥ 4.5:1 contrast ratio
 * - Large text (18pt+ or 14pt+bold): ≥ 3:1 contrast ratio  
 * - UI components: ≥ 3:1 contrast ratio
 */

/**
 * Convert hex color to RGB components
 */
function hexToRgb(hex: string): { r: number; g: number; b: number } {
  // Remove # if present
  hex = hex.replace('#', '');
  
  // Handle 3-digit hex
  if (hex.length === 3) {
    hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
  }
  
  const r = parseInt(hex.substring(0, 2), 16);
  const g = parseInt(hex.substring(2, 4), 16);
  const b = parseInt(hex.substring(4, 6), 16);
  
  return { r, g, b };
}

/**
 * Calculate relative luminance for WCAG contrast calculation
 * Formula from: https://www.w3.org/WAI/GL/wiki/Relative_luminance
 */
function calculateLuminance(r: number, g: number, b: number): number {
  const [rs, gs, bs] = [r / 255, g / 255, b / 255];
  
  const adjust = (c: number): number => {
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  
  const rAdj = adjust(rs);
  const gAdj = adjust(gs);
  const bAdj = adjust(bs);
  
  return 0.2126 * rAdj + 0.7152 * gAdj + 0.0722 * bAdj;
}

/**
 * Calculate contrast ratio between two colors
 * Formula from: https://www.w3.org/WAI/GL/wiki/Contrast_ratio
 */
export function calculateContrastRatio(color1: string, color2: string): number {
  const rgb1 = hexToRgb(color1);
  const rgb2 = hexToRgb(color2);
  
  const l1 = calculateLuminance(rgb1.r, rgb1.g, rgb1.b);
  const l2 = calculateLuminance(rgb2.r, rgb2.g, rgb2.b);
  
  // Ensure L1 is the lighter color (higher luminance)
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  
  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Check if contrast ratio meets WCAG AA requirements
 */
export function meetsWCAGAA(
  foreground: string,
  background: string,
  isLargeText: boolean = false,
  isUIComponent: boolean = false
): boolean {
  const ratio = calculateContrastRatio(foreground, background);
  
  if (isLargeText || isUIComponent) {
    return ratio >= 3.0; // WCAG AA requirement for large text or UI components
  }
  
  return ratio >= 4.5; // WCAG AA requirement for normal text
}

/**
 * Get WCAG compliance level description
 */
export function getWCAGLevel(
  foreground: string,
  background: string
): { level: 'AAA' | 'AA' | 'AA (Large)' | 'Fail'; ratio: number } {
  const ratio = calculateContrastRatio(foreground, background);
  
  if (ratio >= 7.0) {
    return { level: 'AAA', ratio };
  } else if (ratio >= 4.5) {
    return { level: 'AA', ratio };
  } else if (ratio >= 3.0) {
    return { level: 'AA (Large)', ratio };
  } else {
    return { level: 'Fail', ratio };
  }
}

/**
 * Test suite for verifying all theme colors meet WCAG AA requirements
 */
export const themeColorTests = {
  light: {
    colors: {
      primary: '#0066CC',
      secondary: '#F0F4FF',
      accent: '#FF6B35',
      background: '#FFFFFF',
      surface: '#F8F9FA',
      border: '#E0E0E0',
      textPrimary: '#1A1A1A',
      textSecondary: '#666666',
      textMuted: '#999999',
      textInverse: '#FFFFFF',
      statusSuccess: '#10B981',
      statusWarning: '#F59E0B',
      statusError: '#EF4444',
      statusInfo: '#3B82F6',
    },
    statusColors: {
      CREATED: '#3B82F6',
      PREPARING: '#F59E0B',
      READY: '#10B981',
      COMPLETED: '#6B7280',
      CANCELLED: '#EF4444',
    },
  },
  dark: {
    colors: {
      primary: '#60A5FA',
      secondary: '#1E3A5F',
      accent: '#FF8A50',
      background: '#0F1419',
      surface: '#1A202C',
      border: '#374151',
      textPrimary: '#F3F4F6',
      textSecondary: '#D1D5DB',
      textMuted: '#9CA3AF',
      textInverse: '#1A1A1A',
      statusSuccess: '#34D399',
      statusWarning: '#FBBF24',
      statusError: '#F87171',
      statusInfo: '#60A5FA',
    },
    statusColors: {
      CREATED: '#60A5FA',
      PREPARING: '#FBBF24',
      READY: '#34D399',
      COMPLETED: '#9CA3AF',
      CANCELLED: '#F87171',
    },
  },
};

/**
 * Run comprehensive contrast verification for both themes
 */
export function runContrastVerification(): Array<{
  theme: 'light' | 'dark';
  foreground: string;
  background: string;
  ratio: number;
  meetsNormal: boolean;
  meetsLarge: boolean;
  level: 'AAA' | 'AA' | 'AA (Large)' | 'Fail';
}> {
  const results: Array<{
    theme: 'light' | 'dark';
    foreground: string;
    background: string;
    ratio: number;
    meetsNormal: boolean;
    meetsLarge: boolean;
    level: 'AAA' | 'AA' | 'AA (Large)' | 'Fail';
  }> = [];

  const themes = Object.entries(themeColorTests) as Array<['light' | 'dark', typeof themeColorTests.light]>;

  for (const [themeName, theme] of themes) {
    // Test text on backgrounds
    const textColors = [theme.colors.textPrimary, theme.colors.textSecondary, theme.colors.textMuted];
    const backgrounds = [theme.colors.background, theme.colors.surface, theme.colors.border];
    
    for (const fg of textColors) {
      for (const bg of backgrounds) {
        const ratio = calculateContrastRatio(fg, bg);
        const meetsNormal = ratio >= 4.5;
        const meetsLarge = ratio >= 3.0;
        const level = ratio >= 7.0 ? 'AAA' : ratio >= 4.5 ? 'AA' : ratio >= 3.0 ? 'AA (Large)' : 'Fail';
        
        results.push({
          theme: themeName,
          foreground: fg,
          background: bg,
          ratio,
          meetsNormal,
          meetsLarge,
          level,
        });
      }
    }

    // Test status colors on backgrounds (UI components need ≥ 3:1)
    const statusColors = Object.values(theme.statusColors);
    for (const statusColor of statusColors) {
      for (const bg of backgrounds) {
        const ratio = calculateContrastRatio(statusColor, bg);
        const meetsNormal = ratio >= 4.5; // For text if used with status colors
        const meetsLarge = ratio >= 3.0; // For UI components
        const level = ratio >= 7.0 ? 'AAA' : ratio >= 4.5 ? 'AA' : ratio >= 3.0 ? 'AA (Large)' : 'Fail';
        
        results.push({
          theme: themeName,
          foreground: statusColor,
          background: bg,
          ratio,
          meetsNormal,
          meetsLarge,
          level,
        });
      }
    }

    // Test primary/secondary/accent on backgrounds
    const uiColors = [theme.colors.primary, theme.colors.secondary, theme.colors.accent];
    for (const uiColor of uiColors) {
      for (const bg of backgrounds) {
        const ratio = calculateContrastRatio(uiColor, bg);
        const meetsNormal = ratio >= 4.5;
        const meetsLarge = ratio >= 3.0;
        const level = ratio >= 7.0 ? 'AAA' : ratio >= 4.5 ? 'AA' : ratio >= 3.0 ? 'AA (Large)' : 'Fail';
        
        results.push({
          theme: themeName,
          foreground: uiColor,
          background: bg,
          ratio,
          meetsNormal,
          meetsLarge,
          level,
        });
      }
    }
  }

  return results;
}

/**
 * Generate a verification report
 */
export function generateContrastReport(): {
  summary: {
    totalTests: number;
    passedNormal: number;
    passedLarge: number;
    failed: number;
    allPassed: boolean;
  };
  failures: Array<{
    theme: 'light' | 'dark';
    foreground: string;
    background: string;
    ratio: number;
    required: number;
  }>;
} {
  const results = runContrastVerification();
  
  const failures = results.filter(result => !result.meetsLarge); // Use large text standard for UI components
  
  return {
    summary: {
      totalTests: results.length,
      passedNormal: results.filter(r => r.meetsNormal).length,
      passedLarge: results.filter(r => r.meetsLarge).length,
      failed: failures.length,
      allPassed: failures.length === 0,
    },
    failures: failures.map(f => ({
      theme: f.theme,
      foreground: f.foreground,
      background: f.background,
      ratio: f.ratio,
      required: 3.0, // WCAG AA for UI components
    })),
  };
}

// Export for testing
export default {
  calculateContrastRatio,
  meetsWCAGAA,
  getWCAGLevel,
  themeColorTests,
  runContrastVerification,
  generateContrastReport,
};