// Use of this source code is governed by a BSD-style license

// 前端权限判据只有这一份：can(module, action) 与 isAdmin() 供菜单显隐（asideBar 的
// ROUTE_MODULES）、按钮显隐（各页面 v-if="$can(...)"）与用户管理入口（ADMIN）使用。
// 数据来自登录响应的 role 与 /user/permission/self 的有效矩阵（ADMIN=全部动作；
// 某模块无配置行=默认只读 R）。矩阵未加载完成时按「默认只读」保守处理——后端仍是
// 权威，这里只决定用户看得见什么，挡不住直连 API（那由 @RequirePermission 负责）。
import Vue from 'vue'
import http from '../assets/axios.js'
import { getAuth } from './authStorage'

const state = Vue.observable({
  ready: false,
  matrix: {}
})

// 登录响应里的角色（ADMIN 的矩阵恒为全通过，无需请求）
function roleFromLogin () {
  return getAuth('role') === 'ADMIN'
}

export function isAdmin () {
  return roleFromLogin()
}

export function can (module, action) {
  if (isAdmin()) {
    return true
  }
  if (!state.ready) {
    // 拉取完成前只放行读——与后端「未配置默认只读」同一保守取向
    return action === 'R'
  }
  const actions = state.matrix[module]
  return !!actions && actions.indexOf(action) !== -1
}

/**
 * 登录成功后与应用启动（已带 token 的刷新）时各调用一次：拉取当前登录者的有效
 * 矩阵。矩阵是响应式的（Vue.observable），can() 的调用方在拉取完成后自动重渲染；
 * 失败（含 401 被拦截器接管）时保持未加载状态，页面按只读呈现。
 */
export function refreshPermissions (client) {
  const caller = client || http
  return caller.get('/datapoly/manager/api/v1/user/permission/self').then(res => {
    const body = res && res.data
    if (body && 0 === body.code && Array.isArray(body.data)) {
      const matrix = {}
      body.data.forEach(entry => {
        if (entry && entry.module) {
          matrix[entry.module] = entry.actions || []
        }
      })
      state.matrix = matrix
      state.ready = true
    }
    return matrixSnapshot()
  }).catch(() => matrixSnapshot())
}

export function matrixSnapshot () {
  return { ready: state.ready, matrix: state.matrix, admin: isAdmin() }
}

// 测试专用：重置内部状态
export function __resetForTest () {
  state.ready = false
  state.matrix = {}
}

// 测试专用：直接注入一份有效矩阵（绕过 HTTP）
export function __installForTest (matrix, ready) {
  state.matrix = matrix || {}
  state.ready = ready !== false
}
