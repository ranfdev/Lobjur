const Gtk = imports.gi.Gtk;
const Pango = imports.gi.Pango;

const BLOCK_TAGS = new Set([
    'article', 'blockquote', 'div', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'li', 'main', 'ol', 'p', 'pre', 'section', 'ul'
]);

function escapeMarkup(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;');
}

export class Html2GtkStream {
    constructor(rootWidget) {
        this.root = rootWidget || new Gtk.Box({ orientation: Gtk.Orientation.VERTICAL, spacing: 4, hexpand: true });
        this.currentContainer = this.root;
        this.tagStack = [];
        this.inlineStack = [];
        this.listStack = [];
        this.textRuns = [];
        this.lastTextEndedWithWhitespace = true;
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

        const entry = this._openTag(normalizedTag, attributes);
        if (entry) {
            this.tagStack.push(entry);
        }
    }

    endTag(tagName) {
        const normalizedTag = String(tagName || '').toLowerCase();
        if (!normalizedTag) {
            return;
        }

        for (let i = this.tagStack.length - 1; i >= 0; i -= 1) {
            if (this.tagStack[i].tagName === normalizedTag) {
                while (this.tagStack.length > i) {
                    this._closeTopTag();
                }
                return;
            }
        }
    }

    text(content) {
        if (content === null || content === undefined) {
            return;
        }

        const inPre = this._inTag('pre');
        let normalized = String(content);
        if (!inPre) {
            normalized = normalized.replace(/\s+/g, ' ');
            if (normalized.trim() === '') {
                if (this.lastTextEndedWithWhitespace) {
                    return;
                }
                normalized = ' ';
            }
            if (this.lastTextEndedWithWhitespace) {
                normalized = normalized.replace(/^ +/, '');
            }
        }

        if (!normalized) {
            return;
        }

        this.textRuns.push({ text: normalized, style: this._captureInlineStyle() });
        this.lastTextEndedWithWhitespace = /\s$/.test(normalized);
    }

    end() {
        this._flushTextRuns();
        while (this.tagStack.length > 0) {
            this._closeTopTag();
        }
    }

    close() {
        this.end();
    }

    _openTag(tagName, attributes = {}) {
        switch (tagName) {
            case 'br':
                this.text('\n');
                return this._makeTagEntry(tagName, () => {});
            case 'hr': {
                this._flushTextRuns();
                const separator = new Gtk.Separator({ orientation: Gtk.Orientation.HORIZONTAL });
                this._appendToCurrent(separator);
                return this._makeTagEntry(tagName, () => {});
            }
            case 'img': {
                const alt = attributes.alt || '[image]';
                this.text(alt);
                return this._makeTagEntry(tagName, () => {});
            }
            case 'ul':
            case 'ol': {
                this._flushTextRuns();
                const listBox = new Gtk.Box({ orientation: Gtk.Orientation.VERTICAL, spacing: 2, hexpand: true });
                listBox.add_css_class('html-list');
                listBox.add_css_class(tagName === 'ol' ? 'html-ordered-list' : 'html-unordered-list');
                this._applyGlobalAttributes(listBox, attributes);
                const containerEntry = this._pushContainer(tagName, listBox);
                this.listStack.push({ tagName, index: 0 });
                return this._makeTagEntry(tagName, () => {
                    this.listStack.pop();
                    containerEntry.close();
                }, true);
            }
            case 'li': {
                this._flushTextRuns();
                const row = new Gtk.Box({ orientation: Gtk.Orientation.HORIZONTAL, spacing: 6, hexpand: true });
                row.add_css_class('html-list-item');

                const marker = this._nextListMarker();
                const markerLabel = new Gtk.Label({ label: marker, xalign: 0 });
                markerLabel.add_css_class('dim-label');

                const itemContent = new Gtk.Box({ orientation: Gtk.Orientation.VERTICAL, spacing: 2, hexpand: true });
                itemContent.add_css_class('html-list-item-content');
                this._applyGlobalAttributes(itemContent, attributes);

                row.append(markerLabel);
                row.append(itemContent);
                this._appendToCurrent(row);
                return this._pushContainer(tagName, itemContent, false);
            }
            case 'pre': {
                this._flushTextRuns();
                const preScroller = new Gtk.ScrolledWindow({
                    hexpand: true,
                    hscrollbar_policy: Gtk.PolicyType.AUTOMATIC,
                    vscrollbar_policy: Gtk.PolicyType.NEVER
                });
                preScroller.add_css_class('html-pre-scroller');
                const preBox = new Gtk.Box({ orientation: Gtk.Orientation.VERTICAL, spacing: 2, hexpand: true });
                preBox.add_css_class('html-pre');
                this._applyGlobalAttributes(preBox, attributes);
                preScroller.set_child(preBox);
                this._appendToCurrent(preScroller);
                const containerEntry = this._pushContainer(tagName, preBox, false);
                const inlineEntry = this._pushInline(tagName, attributes);
                return this._makeTagEntry(tagName, () => {
                    inlineEntry.close();
                    containerEntry.close();
                }, true);
            }
            case 'blockquote': {
                this._flushTextRuns();
                const quoteBox = new Gtk.Box({ orientation: Gtk.Orientation.VERTICAL, spacing: 4, hexpand: true });
                quoteBox.add_css_class('html-blockquote');
                this._applyGlobalAttributes(quoteBox, attributes);
                return this._pushContainer(tagName, quoteBox);
            }
            case 'p':
            case 'h1':
            case 'h2':
            case 'h3':
            case 'h4':
            case 'h5':
            case 'h6':
            case 'div':
            case 'section':
            case 'article':
            case 'main': {
                this._flushTextRuns();
                const block = new Gtk.Box({ orientation: Gtk.Orientation.VERTICAL, spacing: 2, hexpand: true });
                if (tagName.startsWith('h')) {
                    block.add_css_class('html-heading');
                    block.add_css_class(tagName);
                } else if (tagName === 'p') {
                    block.add_css_class('html-paragraph');
                }
                this._applyGlobalAttributes(block, attributes);
                return this._pushContainer(tagName, block);
            }
            case 'a':
            case 'span':
            case 'code':
            case 'em':
            case 'i':
            case 'strong':
            case 'b':
            case 'u':
            case 's':
                return this._pushInline(tagName, attributes);
            default:
                if (BLOCK_TAGS.has(tagName)) {
                    this._flushTextRuns();
                    const box = new Gtk.Box({ orientation: Gtk.Orientation.VERTICAL, spacing: 2, hexpand: true });
                    this._applyGlobalAttributes(box, attributes);
                    return this._pushContainer(tagName, box);
                }
                return this._makeTagEntry(tagName, () => {});
        }
    }

    _pushContainer(tagName, widget, shouldAppend = true) {
        const parent = this.currentContainer;
        if (shouldAppend) {
            this._appendToCurrent(widget);
        }
        this.currentContainer = widget;
        this.lastTextEndedWithWhitespace = true;
        return this._makeTagEntry(tagName, () => {
            this.currentContainer = parent;
            this.lastTextEndedWithWhitespace = true;
        }, true);
    }

    _pushInline(tagName, attributes) {
        const style = { tagName, attributes };
        this.inlineStack.push(style);
        return this._makeTagEntry(tagName, () => {
            if (this.inlineStack[this.inlineStack.length - 1] === style) {
                this.inlineStack.pop();
                return;
            }
            const idx = this.inlineStack.lastIndexOf(style);
            if (idx >= 0) {
                this.inlineStack.splice(idx, 1);
            }
        });
    }

    _captureInlineStyle() {
        const activeTags = new Set(this.inlineStack.map(style => style.tagName));
        const activeLink = [...this.inlineStack].reverse().find(style => style.tagName === 'a' && style.attributes.href);

        return {
            strong: activeTags.has('strong') || activeTags.has('b'),
            emphasis: activeTags.has('em') || activeTags.has('i'),
            underline: activeTags.has('u'),
            strikethrough: activeTags.has('s'),
            code: activeTags.has('code'),
            pre: activeTags.has('pre'),
            linkHref: activeLink ? activeLink.attributes.href : null
        };
    }

    _flushTextRuns() {
        if (this.textRuns.length === 0) {
            return;
        }

        const runs = this.textRuns;
        this.textRuns = [];

        const inPre = runs.some(run => run.style.pre);
        const hasCode = runs.some(run => run.style.code);

        const label = new Gtk.Label({
            xalign: 0,
            wrap: !inPre,
            selectable: inPre || hasCode,
            hexpand: true
        });

        if (!inPre && typeof label.set_wrap_mode === 'function') {
            label.set_wrap_mode(Pango.WrapMode.WORD_CHAR);
        }

        const markup = runs
            .map(run => this._styleTextRun(run.text, run.style))
            .join('');

        if (typeof label.set_markup === 'function') {
            label.set_markup(markup);
        } else {
            label.set_label(runs.map(run => run.text).join(''));
        }

        this._applyRunClasses(label, runs);
        this._appendToCurrent(label);
    }

    _styleTextRun(text, style) {
        let markup = escapeMarkup(text);
        if (style.code && !style.pre) {
            markup = `<tt>${markup}</tt>`;
        }
        if (style.strong) {
            markup = `<b>${markup}</b>`;
        }
        if (style.emphasis) {
            markup = `<i>${markup}</i>`;
        }
        if (style.underline) {
            markup = `<u>${markup}</u>`;
        }
        if (style.strikethrough) {
            markup = `<s>${markup}</s>`;
        }
        return markup;
    }

    _applyRunClasses(label, runs) {
        if (runs.some(run => run.style.pre)) {
            label.add_css_class('html-pre-text');
        }

        const onlyInlineCode = runs.length > 0 && runs.every(run => run.style.code && !run.style.pre);
        if (onlyInlineCode) {
            label.add_css_class('html-inline-code');
        }

        const links = [...new Set(runs.map(run => run.style.linkHref).filter(Boolean))];
        if (links.length === 1) {
            label.add_css_class('link');
            label.set_tooltip_text(links[0]);
        }
    }

    _applyGlobalAttributes(widget, attrs) {
        if (attrs.class) {
            attrs.class.split(' ').filter(Boolean).forEach(cls => widget.add_css_class(cls));
        }
        if (attrs.id) {
            widget.set_name(attrs.id);
        }
    }

    _appendToCurrent(widget) {
        this._appendTo(this.currentContainer, widget);
    }

    _appendTo(container, widget) {
        if (!container || !widget) {
            return;
        }
        if (typeof container.append === 'function') {
            container.append(widget);
            return;
        }
        if (typeof container.set_child === 'function') {
            const current = typeof container.get_child === 'function' ? container.get_child() : null;
            if (!current) {
                container.set_child(widget);
            } else if (typeof current.append === 'function') {
                current.append(widget);
            }
        }
    }

    _nextListMarker() {
        const currentList = this.listStack[this.listStack.length - 1];
        if (!currentList) {
            return '-';
        }
        if (currentList.tagName === 'ol') {
            currentList.index += 1;
            return `${currentList.index}.`;
        }
        return '-';
    }

    _inTag(tagName) {
        return this.inlineStack.some(entry => entry.tagName === tagName);
    }

    _closeTopTag() {
        const entry = this.tagStack.pop();
        if (!entry) {
            return;
        }

        if (entry.flushBeforeClose) {
            this._flushTextRuns();
        }

        if (typeof entry.close === 'function') {
            entry.close();
        }
    }

    _makeTagEntry(tagName, closeFn, flushBeforeClose = false) {
        return { tagName, close: closeFn || (() => {}), flushBeforeClose };
    }
}
