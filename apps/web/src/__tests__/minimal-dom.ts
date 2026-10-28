// Minimal behavior adapter; it deliberately makes no browser-layout claims.
export class MinimalElement {
  className = '';
  textContent = '';
  disabled = false;
  offsetWidth = 500;
  href = '';
  download = '';
  dataset: Record<string, string> = {};
  attributes = new Map<string, string>();
  children: MinimalElement[] = [];
  events = new Map<string, (event: Record<string, unknown>) => void>();
  focused = false;

  constructor(readonly tagName: string) {}

  get classList() {
    return {
      add: (...classes: string[]) => { this.className = [...new Set([...this.className.split(' '), ...classes])].join(' '); },
      remove: (...classes: string[]) => { this.className = this.className.split(' ').filter((value) => !classes.includes(value)).join(' '); },
    };
  }

  append(...children: MinimalElement[]) { this.children.push(...children); }
  replaceChildren() { this.children = []; }
  setAttribute(name: string, value: string) { this.attributes.set(name, value); }
  addEventListener(name: string, listener: (event: Record<string, unknown>) => void) { this.events.set(name, listener); }
  focus() { this.focused = true; }
  emit(name: string, event: Record<string, unknown> = {}) { this.events.get(name)?.(event); }
  querySelectorAll(selector: string): MinimalElement[] {
    return this.children.flatMap((child) => [
      ...((selector.startsWith('.') ? child.className.split(' ').includes(selector.slice(1)) : child.tagName === selector) ? [child] : []),
      ...child.querySelectorAll(selector),
    ]);
  }
}
