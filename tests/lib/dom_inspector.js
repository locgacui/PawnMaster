/**
 * Lightweight HTML/XML DOM Inspector for Thymeleaf & HTML5 templates
 * Pure Node.js (Zero external dependencies)
 */

const fs = require('fs');

class DomInspector {
  constructor(htmlContent) {
    this.raw = htmlContent;
  }

  static fromFile(filePath) {
    if (!fs.existsSync(filePath)) {
      throw new Error(`File not found: ${filePath}`);
    }
    const content = fs.readFileSync(filePath, 'utf8');
    return new DomInspector(content);
  }

  /**
   * Check if a tag with specific id exists
   */
  hasElementById(id) {
    const regex = new RegExp(`id=["']${id}["']`, 'i');
    return regex.test(this.raw);
  }

  /**
   * Find raw element snippet by ID
   */
  getElementById(id) {
    const regex = new RegExp(`<([a-zA-Z0-9]+)[^>]*id=["']${id}["'][^>]*>`, 'i');
    const match = this.raw.match(regex);
    if (!match) return null;

    const tagName = match[1];
    // Find corresponding block if not void tag
    const voidTags = ['meta', 'link', 'input', 'img', 'br', 'hr', 'source', 'area'];
    if (voidTags.includes(tagName.toLowerCase())) {
      return { tagName, openTag: match[0], content: '', full: match[0] };
    }

    const startIdx = match.index;
    const openTagEnd = startIdx + match[0].length;
    // Extract a chunk starting from startIdx to end tag
    const closeTag = `</${tagName}>`;
    const endIdx = this.raw.indexOf(closeTag, openTagEnd);
    if (endIdx === -1) {
      return { tagName, openTag: match[0], content: '', full: match[0] };
    }

    return {
      tagName,
      openTag: match[0],
      content: this.raw.substring(openTagEnd, endIdx),
      full: this.raw.substring(startIdx, endIdx + closeTag.length)
    };
  }

  /**
   * Find elements matching a specific class name
   */
  getElementsByClassName(className) {
    const regex = new RegExp(`<([a-zA-Z0-9]+)[^>]*class=["'][^"']*\\b${className}\\b[^"']*["'][^>]*>`, 'gi');
    const matches = [];
    let match;
    while ((match = regex.exec(this.raw)) !== null) {
      matches.push({
        tagName: match[1],
        openTag: match[0]
      });
    }
    return matches;
  }

  /**
   * Find all occurrences of an attribute name
   */
  getAttributes(attrName) {
    const regex = new RegExp(`${attrName}=["']([^"']*)["']`, 'gi');
    const results = [];
    let match;
    while ((match = regex.exec(this.raw)) !== null) {
      results.push(match[1]);
    }
    return results;
  }

  /**
   * Checks if string or pattern is present
   */
  contains(pattern) {
    if (pattern instanceof RegExp) {
      return pattern.test(this.raw);
    }
    return this.raw.includes(pattern);
  }

  /**
   * Validate XML/HTML5 tag closing:
   * Checks that all void tags (<meta>, <link>, <input>, <img>, <hr>, <br>) are closed with '/>' or properly formed,
   * and that every opening tag (e.g. <div>) has a matching closing tag (</div>).
   */
  validateXmlStructure() {
    const errors = [];

    // Strip HTML comments and script/style contents for tag balance check
    let clean = this.raw.replace(/<!--[\s\S]*?-->/g, '');
    clean = clean.replace(/<script[\s\S]*?<\/script>/gi, '<script></script>');
    clean = clean.replace(/<style[\s\S]*?<\/style>/gi, '<style></style>');

    const voidElements = new Set(['meta', 'link', 'input', 'img', 'br', 'hr', 'source', 'area', 'base', 'col', 'embed', 'param', 'track', 'wbr']);
    const tagRegex = /<\/?([a-zA-Z0-9:-]+)(\s+[^>]*)?(\/?)>/g;

    const stack = [];
    let match;

    while ((match = tagRegex.exec(clean)) !== null) {
      const fullTag = match[0];
      const tagName = match[1].toLowerCase();
      const isSelfClosing = match[3] === '/' || fullTag.endsWith('/>');
      const isClosing = fullTag.startsWith('</');

      // Check DOCTYPE
      if (tagName === '!doctype') continue;

      if (voidElements.has(tagName)) {
        // Void elements: in XML/Thymeleaf dual-mode, must ideally be self-closed <... />
        if (!isSelfClosing && !fullTag.endsWith('/>')) {
          errors.push(`Void element <${tagName}> is not self-closed with '/>' (required for Thymeleaf XML compatibility): ${fullTag.slice(0, 50)}`);
        }
        continue;
      }

      if (isClosing) {
        if (stack.length === 0) {
          errors.push(`Unmatched closing tag: </${tagName}>`);
        } else {
          const top = stack.pop();
          if (top.tagName !== tagName) {
            errors.push(`Mismatched closing tag: expected </${top.tagName}>, found </${tagName}>`);
          }
        }
      } else if (!isSelfClosing) {
        stack.push({ tagName, fullTag });
      }
    }

    if (stack.length > 0) {
      for (const unclosed of stack) {
        errors.push(`Unclosed tag: <${unclosed.tagName}>`);
      }
    }

    return {
      isValid: errors.length === 0,
      errors
    };
  }

  /**
   * Check for forbidden nested cards in DOM
   */
  findNestedCards() {
    const cardBlocks = [];
    const cardRegex = /<([a-zA-Z0-9]+)[^>]*class=["'][^"']*\b(?:card|pm-kpi-card)\b[^"']*["'][^>]*>/gi;
    let match;

    // Scan for card tags and see if another card is nested inside before closing tag
    while ((match = cardRegex.exec(this.raw)) !== null) {
      const startIdx = match.index;
      const tagName = match[1];
      const endTag = `</${tagName}>`;
      const nextCardIdx = this.raw.indexOf('class="card', startIdx + match[0].length);
      const closeIdx = this.raw.indexOf(endTag, startIdx + match[0].length);

      if (nextCardIdx !== -1 && closeIdx !== -1 && nextCardIdx < closeIdx) {
        cardBlocks.push({
          parentCard: match[0],
          nestedLocation: nextCardIdx
        });
      }
    }

    return cardBlocks;
  }
}

module.exports = DomInspector;
