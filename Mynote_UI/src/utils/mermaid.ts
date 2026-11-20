import mermaid from 'mermaid'

/**
 * Mermaid 渲染工具。
 *
 * 逻辑图用 Mermaid 而不是 PlantUML：PlantUML 需要 Java + Graphviz 的服务端渲染，
 * 与本产品"单机、少中间件"的前提冲突；Mermaid 在浏览器里直接出 SVG，
 * 语法也和后端 DiagramService 生成的代码一致。
 */

let initialized = false

/** 主题色跟随用户自定义主色，避免图和内容两套色感 */
export function initMermaid(brand: string, dark: boolean) {
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'loose',
    theme: 'base',
    fontFamily: '"PingFang SC","Microsoft YaHei",sans-serif',
    flowchart: { curve: 'basis', padding: 12, useMaxWidth: true },
    mindmap: { padding: 16, useMaxWidth: true },
    themeVariables: dark
      ? {
          primaryColor: mix(brand, '#241d19', 0.35),
          primaryTextColor: '#f0e6da',
          primaryBorderColor: brand,
          lineColor: mix(brand, '#f0e6da', 0.45),
          secondaryColor: mix(brand, '#241d19', 0.5),
          tertiaryColor: '#2c2521',
          background: '#2c2521',
          mainBkg: mix(brand, '#2c2521', 0.28),
          nodeBorder: brand,
          clusterBkg: '#352d28',
          titleColor: '#f0e6da',
        }
      : {
          primaryColor: mix(brand, '#ffffff', 0.82),
          primaryTextColor: '#3a2e28',
          primaryBorderColor: mix(brand, '#3a2e28', 0.65),
          lineColor: mix(brand, '#8b7a6d', 0.55),
          secondaryColor: mix(brand, '#faf4ec', 0.85),
          tertiaryColor: '#faf4ec',
          background: '#fffdf9',
          mainBkg: mix(brand, '#ffffff', 0.88),
          nodeBorder: mix(brand, '#3a2e28', 0.6),
          clusterBkg: mix(brand, '#faf4ec', 0.92),
          titleColor: '#3a2e28',
        },
  })
  initialized = true
}

/**
 * 简易 sRGB 混合，用来从用户主色派生图配色。
 *
 * @param a 颜色 A
 * @param b 颜色 B
 * @param ratio A 的占比
 * @returns #rrggbb
 */
function mix(a: string, b: string, ratio: number): string {
  const parse = (hex: string) => {
    const clean = hex.replace('#', '')
    const full = clean.length === 3 ? clean.split('').map((c) => c + c).join('') : clean
    return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16))
  }
  const ca = parse(a)
  const cb = parse(b)
  const out = ca.map((v, i) => Math.round(v * ratio + (cb[i] ?? 0) * (1 - ratio)))
  return `#${out.map((v) => v.toString(16).padStart(2, '0')).join('')}`
}

let seq = 0

/**
 * 把 Mermaid 源码渲染成 SVG 字符串。
 *
 * @param code Mermaid 源码
 * @returns SVG 文本；语法错误时抛异常，由调用方展示"无法渲染"提示
 */
export async function renderMermaid(code: string): Promise<string> {
  if (!initialized) initMermaid('#C06A46', false)
  seq += 1
  const id = `mynote-diagram-${seq}`
  const { svg } = await mermaid.render(id, code)
  // mermaid 会遗留一个隐藏容器，渲染完就清掉，避免多次生成后 DOM 累积
  document.getElementById(`d${id}`)?.remove()
  return svg
}

/** 判断能否解析，用于在保存前给用户即时反馈 */
export async function validateMermaid(code: string): Promise<boolean> {
  try {
    await mermaid.parse(code)
    return true
  } catch {
    return false
  }
}
