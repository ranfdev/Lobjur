const SELF_CLOSING_TAGS = new Set(['br', 'hr', 'img']);

const ENTITY_MAP = {
    '&amp;': '&',
    '&lt;': '<',
    '&gt;': '>',
    '&quot;': '"',
    '&#39;': '\'',
    '&#x27;': '\'',
    '&#x2f;': '/',
    '&nbsp;': ' '
};

function decodeEntities(value) {
    if (!value) {
        return '';
    }
    return value
        .replace(/&#(\d+);/g, (_m, code) => String.fromCharCode(parseInt(code, 10)))
        .replace(/&#x([0-9a-f]+);/gi, (_m, code) => String.fromCharCode(parseInt(code, 16)))
        .replace(/&(amp|lt|gt|quot|nbsp);|&#39;|&#x27;|&#x2f;/gi, match => ENTITY_MAP[match.toLowerCase()] || match);
}

export class Text2HtmlStream {
    constructor(target = null) {
        this.target = target || {};
        this.pending = '';
        this.seen = new Set();
        this.ended = false;
    }

    _callTarget(method, ...args) {
        if (this.target && typeof this.target[method] === 'function') {
            this.target[method](...args);
        }
    }

    writeObject(obj) {
        if (obj === null || obj === undefined) {
            return;
        }

        if (typeof obj === 'string' || typeof obj === 'number' || typeof obj === 'boolean') {
            this.writeChunk(String(obj));
            return;
        }

        if (Array.isArray(obj)) {
            for (const item of obj) {
                this.writeObject(item);
            }
            return;
        }

        if (typeof obj === 'object') {
            if (this.seen.has(obj)) {
                this.text('<cycle>');
                return;
            }
            this.seen.add(obj);
            try {
                if (typeof obj.writeOnHtml2Gtk === 'function') {
                    obj.writeOnHtml2Gtk(this);
                    return;
                }
                if (typeof obj.writeOn === 'function') {
                    obj.writeOn(this);
                    return;
                }
                this.writeChunk(String(obj));
            } finally {
                this.seen.delete(obj);
            }
        }
    }

    write(chunk) {
        this.writeChunk(chunk);
    }

    writeChunk(chunk) {
        if (this.ended) {
            return;
        }

        const input = this.pending + String(chunk ?? '');
        this.pending = '';

        let cursor = 0;
        while (cursor < input.length) {
            const lt = input.indexOf('<', cursor);
            if (lt === -1) {
                this.text(input.slice(cursor));
                break;
            }

            if (lt > cursor) {
                this.text(input.slice(cursor, lt));
            }

            const gt = input.indexOf('>', lt + 1);
            if (gt === -1) {
                this.pending = input.slice(lt);
                return;
            }

            this._processTagToken(input.slice(lt + 1, gt));
            cursor = gt + 1;
        }
    }

    end() {
        if (this.ended) {
            return;
        }

        if (this.pending) {
            this.text(this.pending);
            this.pending = '';
        }

        this.ended = true;
        this._callTarget('end');
    }

    close() {
        this.end();
        this._callTarget('close');
    }

    tag(tagName, attributes = {}, bodyFn) {
        this.startTag(tagName, attributes);
        if (bodyFn) {
            bodyFn(this);
        }
        this.endTag(tagName);
    }

    startTag(tagName, attributes = {}) {
        const normalizedTag = String(tagName || '').toLowerCase();
        if (!normalizedTag) {
            return;
        }
        this._callTarget('startTag', normalizedTag, attributes);
    }

    endTag(tagName) {
        const normalizedTag = String(tagName || '').toLowerCase();
        if (!normalizedTag) {
            return;
        }
        this._callTarget('endTag', normalizedTag);
    }

    text(content) {
        if (content === null || content === undefined) {
            return;
        }

        this._callTarget('text', decodeEntities(String(content)));
    }

    _processTagToken(token) {
        const trimmed = String(token || '').trim();
        if (!trimmed || trimmed.startsWith('!')) {
            return;
        }

        if (trimmed.startsWith('/')) {
            this.endTag(trimmed.slice(1).trim().toLowerCase());
            return;
        }

        const selfClosing = trimmed.endsWith('/');
        const normalized = selfClosing ? trimmed.slice(0, -1).trim() : trimmed;
        const tagNameMatch = normalized.match(/^([^\s/>]+)/);
        if (!tagNameMatch) {
            return;
        }

        const tagName = tagNameMatch[1].toLowerCase();
        const attrs = this._parseAttributes(normalized.slice(tagNameMatch[0].length));
        this.startTag(tagName, attrs);
        if (selfClosing || SELF_CLOSING_TAGS.has(tagName)) {
            this.endTag(tagName);
        }
    }

    _parseAttributes(rawAttrs) {
        const attributes = {};
        const attrRegex = /([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;
        let match;
        while ((match = attrRegex.exec(rawAttrs || '')) !== null) {
            const key = match[1].toLowerCase();
            const value = match[2] ?? match[3] ?? match[4] ?? '';
            attributes[key] = decodeEntities(value);
        }
        return attributes;
    }
}
