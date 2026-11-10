/**
 * WCAG AA Contrast Ratio Calculator
 * Formula according to W3C WCAG 2.1 specifications:
 * Relative luminance: L = 0.2126 * R + 0.7152 * G + 0.0722 * B
 * Contrast ratio: CR = (L1 + 0.05) / (L2 + 0.05) where L1 >= L2
 */

function parseColorToRgb(colorStr) {
  if (!colorStr) return null;
  const str = colorStr.trim().toLowerCase();

  // Hex #ffffff or #fff
  if (str.startsWith('#')) {
    let hex = str.slice(1);
    if (hex.length === 3) {
      hex = hex.split('').map(c => c + c).join('');
    }
    if (hex.length === 6) {
      const r = parseInt(hex.substring(0, 2), 16);
      const g = parseInt(hex.substring(2, 4), 16);
      const b = parseInt(hex.substring(4, 6), 16);
      return { r, g, b };
    }
    if (hex.length === 8) {
      // Hex with alpha
      const r = parseInt(hex.substring(0, 2), 16);
      const g = parseInt(hex.substring(2, 4), 16);
      const b = parseInt(hex.substring(4, 6), 16);
      return { r, g, b };
    }
  }

  // rgb(r, g, b) or rgba(r, g, b, a)
  const rgbMatch = str.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
  if (rgbMatch) {
    return {
      r: parseInt(rgbMatch[1], 10),
      g: parseInt(rgbMatch[2], 10),
      b: parseInt(rgbMatch[3], 10)
    };
  }

  // Named colors
  const named = {
    white: { r: 255, g: 255, b: 255 },
    black: { r: 0, g: 0, b: 0 }
  };
  if (named[str]) return named[str];

  return null;
}

function sRgbToLinear(val) {
  const v = val / 255;
  return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
}

function getRelativeLuminance(rgb) {
  const r = sRgbToLinear(rgb.r);
  const g = sRgbToLinear(rgb.g);
  const b = sRgbToLinear(rgb.b);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function getContrastRatio(colorA, colorB) {
  const rgbA = typeof colorA === 'string' ? parseColorToRgb(colorA) : colorA;
  const rgbB = typeof colorB === 'string' ? parseColorToRgb(colorB) : colorB;

  if (!rgbA || !rgbB) {
    throw new Error(`Invalid color for contrast calculation: ${colorA}, ${colorB}`);
  }

  const lumA = getRelativeLuminance(rgbA);
  const lumB = getRelativeLuminance(rgbB);

  const l1 = Math.max(lumA, lumB);
  const l2 = Math.min(lumA, lumB);

  return (l1 + 0.05) / (l2 + 0.05);
}

function passesWcagAA(colorA, colorB, isLargeText = false) {
  const ratio = getContrastRatio(colorA, colorB);
  const threshold = isLargeText ? 3.0 : 4.5;
  return {
    ratio: Math.round(ratio * 100) / 100,
    passes: ratio >= threshold,
    threshold
  };
}

module.exports = {
  parseColorToRgb,
  getRelativeLuminance,
  getContrastRatio,
  passesWcagAA
};
