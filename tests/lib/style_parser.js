/**
 * Style and CSS Token Parser
 * Extracts :root custom properties, class rules, and validates against anti-patterns
 */

const fs = require('fs');

class StyleParser {
  constructor(cssContent) {
    this.raw = cssContent;
    this.variables = this._extractRootVariables();
    this.rules = this._extractRules();
  }

  static fromFile(filePath) {
    if (!fs.existsSync(filePath)) {
      throw new Error(`CSS file not found: ${filePath}`);
    }
    const content = fs.readFileSync(filePath, 'utf8');
    return new StyleParser(content);
  }

  _extractRootVariables() {
    const vars = {};
    const rootMatch = this.raw.match(/:root\s*\{([^}]+)\}/s);
    if (!rootMatch) return vars;

    const declarations = rootMatch[1].split(';');
    for (const decl of declarations) {
      const match = decl.match(/(--[\w-]+)\s*:\s*([^;]+)/);
      if (match) {
        vars[match[1].trim()] = match[2].trim();
      }
    }
    return vars;
  }

  _extractRules() {
    const rules = [];
    // Basic CSS rule extractor
    const cleanCss = this.raw.replace(/\/\*[\s\S]*?\*\//g, '');
    const ruleRegex = /([^{]+)\{([^}]+)\}/g;
    let match;
    while ((match = ruleRegex.exec(cleanCss)) !== null) {
      const selector = match[1].trim();
      const body = match[2].trim();
      if (!selector.startsWith('@keyframes') && !selector.startsWith('@media')) {
        rules.push({ selector, body });
      }
    }
    return rules;
  }

  getVariable(varName) {
    return this.variables[varName] || null;
  }

  hasVariable(varName) {
    return Object.prototype.hasOwnProperty.call(this.variables, varName);
  }

  findRuleBySelector(selectorPart) {
    return this.rules.filter(r => r.selector.includes(selectorPart));
  }

  hasPropertyInRule(selectorPart, propName, valueSubstr = null) {
    const matchingRules = this.findRuleBySelector(selectorPart);
    for (const r of matchingRules) {
      const propRegex = new RegExp(`${propName}\\s*:\\s*([^;]+)`, 'i');
      const match = r.body.match(propRegex);
      if (match) {
        if (!valueSubstr) return true;
        if (match[1].toLowerCase().includes(valueSubstr.toLowerCase())) return true;
      }
    }
    return false;
  }

  detectAntiPatterns() {
    const violations = [];

    // 1. Generic Purple-to-Blue Gradients
    // Forbidden examples: linear-gradient(... #667eea ... #764ba2), #8b5cf6, purple, violet
    const purpleBlueGradientRegex = /linear-gradient\([^)]*(#667eea|#764ba2|#8b5cf6|#a855f7|purple|violet)[\s\S]*?(#3b82f6|#2563eb|#1d4ed8|#60a5fa|blue)/i;
    const reverseGradientRegex = /linear-gradient\([^)]*(#3b82f6|#2563eb|#1d4ed8|#60a5fa|blue)[\s\S]*?(#667eea|#764ba2|#8b5cf6|#a855f7|purple|violet)/i;
    
    if (purpleBlueGradientRegex.test(this.raw) || reverseGradientRegex.test(this.raw)) {
      violations.push({
        type: 'PURPLE_BLUE_GRADIENT',
        detail: 'Found generic fintech purple-to-blue gradient in CSS'
      });
    }

    // 2. Neon Glow Drop Shadows
    // e.g. box-shadow: 0 0 ...px rgba(0, 255, 200, ...) or #00ffcc, #39ff14
    const neonGlowRegex = /box-shadow\s*:[^;]*(rgba?\(\s*0\s*,\s*255|#00ff[a-f0-9]{2}|#39ff14|#ff007f)/i;
    if (neonGlowRegex.test(this.raw)) {
      violations.push({
        type: 'NEON_GLOW',
        detail: 'Found unmotivated neon glow box-shadow in CSS'
      });
    }

    // 3. Nested Card selectors (.card .card)
    const nestedCardRegex = /\.card\s+[^{,]*\.card/i;
    if (nestedCardRegex.test(this.raw)) {
      violations.push({
        type: 'NESTED_CARD_RULE',
        detail: 'Found nested card styling rule (.card .card) in CSS'
      });
    }

    return violations;
  }
}

module.exports = StyleParser;
