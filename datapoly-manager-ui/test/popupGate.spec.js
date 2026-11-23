// Use of this source code is governed by a BSD-style license
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// 每个用例重新载入模块：去重状态是模块级单例，包装也一次性生效
async function loadGate () {
  vi.resetModules()
  return await import('../src/utils/popupGate.js')
}

function fakeVue () {
  return {
    prototype: {
      $alert: vi.fn(() => Promise.resolve()),
      $message: vi.fn(),
      $confirm: vi.fn(),
      $prompt: vi.fn()
    }
  }
}

function installNativeAlert () {
  const native = vi.fn()
  window.alert = native
  return native
}

describe('popupGate', () => {
  let originalAlert

  beforeEach(() => {
    vi.useFakeTimers()
    originalAlert = window.alert
  })

  afterEach(() => {
    vi.useRealTimers()
    window.alert = originalAlert
  })

  it('原生 alert 同一文案 3 秒内只弹一次', async () => {
    const { installPopupGate } = await loadGate()
    const native = installNativeAlert()
    installPopupGate(fakeVue())

    window.alert('加载失败')
    window.alert('加载失败')
    expect(native).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(2999)
    window.alert('加载失败')
    expect(native).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(2)
    window.alert('加载失败')
    expect(native).toHaveBeenCalledTimes(2)
  })

  it('窗口从弹窗关闭时刻起算，弹窗本身停留的时间不计入', async () => {
    const { installPopupGate } = await loadGate()
    const native = vi.fn(() => {
      // 模拟原生 alert 阻塞：用户 5 秒后才点掉
      vi.advanceTimersByTime(5000)
    })
    window.alert = native
    installPopupGate(fakeVue())

    window.alert('登录已失效')
    window.alert('登录已失效')
    expect(native).toHaveBeenCalledTimes(1)
  })

  it('$alert 在 Promise 结束（关闭）后 3 秒内去重', async () => {
    const { installPopupGate } = await loadGate()
    const vue = fakeVue()
    const raw = vue.prototype.$alert
    installPopupGate(vue)

    vue.prototype.$alert('出错了')
    // 尚未关闭：重复调用被吞
    vue.prototype.$alert('出错了')
    expect(raw).toHaveBeenCalledTimes(1)

    await Promise.resolve()
    // 刚关闭，仍在窗口内
    vue.prototype.$alert('出错了')
    expect(raw).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(3001)
    vue.prototype.$alert('出错了')
    expect(raw).toHaveBeenCalledTimes(2)
  })

  it('$message 按文案去重（对象形态取 message 字段）', async () => {
    const { installPopupGate } = await loadGate()
    const vue = fakeVue()
    const raw = vue.prototype.$message
    installPopupGate(vue)

    vue.prototype.$message({type: 'error', message: '加载列表失败'})
    vue.prototype.$message({type: 'error', message: '加载列表失败'})
    expect(raw).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(3001)
    vue.prototype.$message({type: 'error', message: '加载列表失败'})
    expect(raw).toHaveBeenCalledTimes(2)
  })

  it('$message 的类型化方法（success/error 等）保留且各自过闸门', async () => {
    const { installPopupGate } = await loadGate()
    const vue = fakeVue()
    // element-ui 把 success/warning/info/error 与 closeAll 挂在 Message 函数对象上
    const raw = vue.prototype.$message
    ;['success', 'warning', 'info', 'error'].forEach(type => { raw[type] = vi.fn() })
    raw.closeAll = vi.fn()
    installPopupGate(vue)
    const gated = vue.prototype.$message

    // 包装之后仍是可调用的方法；页面上十来处 this.$message.error(...) 靠它们
    ;['success', 'warning', 'info', 'error'].forEach(type => {
      expect(typeof gated[type]).toBe('function')
    })
    expect(gated.closeAll).toBe(raw.closeAll)

    gated.success('导入成功')
    gated.success('导入成功')
    expect(raw.success).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(3001)
    gated.success('导入成功')
    expect(raw.success).toHaveBeenCalledTimes(2)

    // 各类型各自透传，不会被别的类型吞掉
    gated.error('加载失败')
    expect(raw.error).toHaveBeenCalledTimes(1)
  })

  it('$message 的类型化方法与普通调用共用同一把闸门', async () => {
    const { installPopupGate } = await loadGate()
    const vue = fakeVue()
    const raw = vue.prototype.$message
    raw.success = vi.fn()
    installPopupGate(vue)
    const gated = vue.prototype.$message

    gated.success('保存成功')
    gated({type: 'success', message: '保存成功'})
    expect(raw.success).toHaveBeenCalledTimes(1)
    expect(raw).not.toHaveBeenCalled()
  })

  it('登记的登录态 message 合并不同页面前缀的同一次失败', async () => {
    const { installPopupGate, noteAuthMessage } = await loadGate()
    const native = installNativeAlert()
    installPopupGate(fakeVue())

    noteAuthMessage('Token已过期，请重新登录')
    window.alert('加载失败：Token已过期，请重新登录')
    window.alert('加载列表失败:Token已过期，请重新登录')
    expect(native).toHaveBeenCalledTimes(1)
  })

  it('$confirm/$prompt 不包装（提问必须每次都出）', async () => {
    const { installPopupGate } = await loadGate()
    const vue = fakeVue()
    const confirm = vue.prototype.$confirm
    const prompt = vue.prototype.$prompt
    installPopupGate(vue)
    expect(vue.prototype.$confirm).toBe(confirm)
    expect(vue.prototype.$prompt).toBe(prompt)
  })
})