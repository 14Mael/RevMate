/**
 * RevMate axios 实例
 * - baseURL: /api（由 Vite dev server 代理到 http://localhost:8080）
 * - 请求拦截：自动注入 Bearer token
 * - 响应拦截：解包 Result<T>；401 清 token 跳登录；非 0 code 弹 ElMessage
 */

import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from './types'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000
})

http.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

http.interceptors.response.use(
  (response: AxiosResponse<Result<unknown>>) => {
    const body = response.data
    if (!body || typeof body !== 'object' || !('code' in body)) {
      // 非 Result 包装（极少数接口或下载流），原样返回
      return response
    }
    if (body.code === 0) {
      return body.data as unknown as AxiosResponse
    }
    // 业务错误
    ElMessage.error(body.message || `请求失败 (code=${body.code})`)
    return Promise.reject(new Error(body.message || `Business error ${body.code}`))
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      const userStore = useUserStore()
      userStore.clear()
      ElMessage.warning('登录已过期，请重新登录')
      router.push('/login')
    } else if (status === 403) {
      ElMessage.error('没有权限执行此操作')
    } else if (status && status >= 500) {
      ElMessage.error('服务器开小差了，请稍后重试')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络')
    } else if (!status) {
      ElMessage.error('网络连接异常，请检查后重试')
    } else {
      ElMessage.error(error.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

/** 统一 request 包装：直接返回 Result.data（已解包） */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  return http.request<Result<T>, T>(config)
}

export default http
