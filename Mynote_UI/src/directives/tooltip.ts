import type { Directive, DirectiveBinding } from 'vue'

/**
 * 全局自定义悬浮提示 v-tooltip。
 *
 * 原生 title 只能给浏览器默认的灰底小黑条，既慢又没法贴合「小窝」的纸面视觉。
 * 这里用一个挂在 body 上的单例浮层，样式走 tokens.css 的纸面令牌，与整体风格统一。
 * 用法：v-tooltip="'提示文案'" 或 v-tooltip="动态表达式"（值为空则不出提示）。
 */
type TooltipValue = string | null | undefined

let tipEl: HTMLElement | null = null

/** 每个元素当前的提示文案，避免闭包捕获旧值导致动态文案失效 */
const texts = new WeakMap<HTMLElement, string>()

/** 每个元素挂上的事件句柄，卸载时好摘掉 */
const handlers = new WeakMap<HTMLElement, { enter: () => void; leave: () => void }>()

function ensureTip(): HTMLElement {
  if (!tipEl) {
    tipEl = document.createElement('div')
    tipEl.className = 'mynote-tooltip'
    tipEl.setAttribute('role', 'tooltip')
    document.body.appendChild(tipEl)
  }
  return tipEl
}

function place(tip: HTMLElement, target: HTMLElement) {
  const targetRect = target.getBoundingClientRect()
  const tipRect = tip.getBoundingClientRect()
  const gap = 8
  const margin = 8

  let left = targetRect.left + targetRect.width / 2 - tipRect.width / 2
  let top = targetRect.top - tipRect.height - gap

  left = Math.max(margin, Math.min(left, window.innerWidth - tipRect.width - margin))
  if (top < margin) {
    top = targetRect.bottom + gap
  }

  tip.style.left = `${Math.round(left)}px`
  tip.style.top = `${Math.round(top)}px`
}

function show(target: HTMLElement, text: string) {
  const tip = ensureTip()
  tip.textContent = text
  tip.classList.add('is-visible')
  place(tip, target)
}

function hide() {
  tipEl?.classList.remove('is-visible')
}

export const tooltip: Directive<HTMLElement, TooltipValue> = {
  mounted(el, binding: DirectiveBinding<TooltipValue>) {
    const enter = () => {
      const text = texts.get(el)
      if (text) show(el, text)
    }
    const leave = () => hide()
    texts.set(el, binding.value ?? '')
    handlers.set(el, { enter, leave })
    el.addEventListener('mouseenter', enter)
    el.addEventListener('mouseleave', leave)
  },
  updated(el, binding: DirectiveBinding<TooltipValue>) {
    texts.set(el, binding.value ?? '')
    if (tipEl?.classList.contains('is-visible')) {
      const text = binding.value
      if (text) show(el, text)
    }
  },
  unmounted(el) {
    const h = handlers.get(el)
    if (h) {
      el.removeEventListener('mouseenter', h.enter)
      el.removeEventListener('mouseleave', h.leave)
    }
    texts.delete(el)
    handlers.delete(el)
  },
}

declare module 'vue' {
  interface GlobalDirectives {
    tooltip: Directive<HTMLElement, TooltipValue>
  }
}
