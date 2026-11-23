// Use of this source code is governed by a BSD-style license
import { describe, it, expect, beforeAll } from 'vitest'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { parseComponent, compile } from 'vue-template-compiler'

// 项目没有组件级测试工具链：用构建自带的 vue-template-compiler 现场编译 SFC 再挂载。
// vue / element-ui 都经同一个 require 取得，保证只有一个 Vue 实例。
const nodeRequire = createRequire(import.meta.url)
const Vue = nodeRequire('vue')
Vue.use(nodeRequire('element-ui'))
Vue.config.productionTip = false
Vue.config.devtools = false
Vue.prototype.$t = key => key

function loadComponent (relativePath) {
  const sfc = readFileSync(new URL(relativePath, import.meta.url), 'utf8')
  const { template, script } = parseComponent(sfc)
  const compiled = compile(template.content)
  if (compiled.errors.length) {
    throw new Error('template compile failed: ' + compiled.errors.join('\n'))
  }
  // 脚本里的 import 改写成 require（样式导入丢弃），才能直接在 Node 里求值
  const code = script.content
    .replace(/^\s*import\s+['"][^'"]+\.css['"];?\s*$/gm, '')
    .replace(/^\s*import\s+['"]([^'"]+)['"];?\s*$/gm, "require('$1');")
    .replace(/import\s+\{\s*codemirror\s*\}\s+from\s+['"]vue-codemirror['"]/,
      "const { codemirror } = require('vue-codemirror')")
    .replace(/export default/, 'return')
  const options = new Function('require', code)(nodeRequire)
  return Vue.extend(Object.assign({}, options, {
    render: new Function(compiled.render),
    staticRenderFns: compiled.staticRenderFns.map(fn => new Function(fn))
  }))
}

// CodeMirror 在初始化与赋值时会量取光标位置，jsdom 没有布局
beforeAll(() => {
  window.Range.prototype.getBoundingClientRect = () => ({ left: 0, right: 0, top: 0, bottom: 0, width: 0, height: 0 })
  window.Range.prototype.getClientRects = () => []
})

const MultiSqlEditer = loadComponent('../src/components/codeEditer/multiSqlEditer.vue')
const tick = () => new Promise(resolve => setTimeout(resolve, 0))

// 与 interface/common.vue 的用法一致：<multi-sql-editer :tabSqls="createParam.sqls">
function mountWith (sqls) {
  const Parent = Vue.extend({
    data: () => ({ sqls }),
    render (h) {
      return h(MultiSqlEditer, { ref: 'editor', props: { tabSqls: this.sqls } })
    }
  })
  const parent = new Parent().$mount()
  return { parent, editor: parent.$refs.editor }
}

// 切换版本：先 resetEditor() 再换 tabSqls，即 applyAssignmentDetail 的顺序
async function switchTo (mounted, sqls) {
  mounted.editor.resetEditor()
  mounted.parent.sqls = sqls
  await tick()
  await tick()
}

describe('multiSqlEditer 的编辑器登记表（保存时提交的内容）', () => {
  it('一次载入多个 SQL 窗口后，保存读到的是全部窗口', async () => {
    const mounted = mountWith(['select 1', 'select 2', 'select 3'])
    await tick()
    await tick()

    expect(mounted.editor.queryContent()).toEqual(['select 1', 'select 2', 'select 3'])
    mounted.parent.$destroy()
  })

  it('非最后一个窗口上也能读到当前标签页的 SQL', async () => {
    const mounted = mountWith(['select 1', 'select 2', 'select 3'])
    await tick()
    await tick()
    mounted.editor.currentTabName = '1'
    await tick()

    expect(mounted.editor.queryCurrentTabSql()).toBe('select 1')
    mounted.parent.$destroy()
  })

  it('切换到窗口更少的版本后，保存读不到上个版本残留的 SQL', async () => {
    const mounted = mountWith(['select 1', 'select 2', 'select 3'])
    await tick()
    await tick()

    await switchTo(mounted, ['select new'])
    expect(mounted.editor.queryContent()).toEqual(['select new'])

    await switchTo(mounted, ['select a', 'select b'])
    expect(mounted.editor.queryContent()).toEqual(['select a', 'select b'])
    mounted.parent.$destroy()
  })

  it('手动新增与关闭窗口后，登记表随之增减', async () => {
    const mounted = mountWith(['select a', 'select b'])
    await tick()
    await tick()

    mounted.editor.addTab()
    await tick()
    await tick()
    expect(mounted.editor.queryContent()).toEqual(['select a', 'select b', ''])

    mounted.editor.removeTab('2')
    await tick()
    await tick()
    expect(mounted.editor.queryContent()).toEqual(['select a', ''])
    mounted.parent.$destroy()
  })
})
