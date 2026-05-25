import axios, { type AxiosInstance, AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一的 axios 实例。
 *
 * - baseURL = '/api'：dev 模式下被 Vite proxy 转发到后端 :8080；
 *                     生产可在部署层用 nginx/反向代理处理同源
 * - 错误统一弹 Element Plus toast
 * - 自动注入 X-Caller-Id = 'src-rule-web'，匹配后端 InvocationContext 追溯设计
 */
const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30_000,
  headers: {
    'Content-Type': 'application/json',
    'X-Caller-Id': 'src-rule-web',
  },
})

http.interceptors.response.use(
  (resp) => resp,
  (err: AxiosError<{ message?: string; code?: string }>) => {
    const msg = err.response?.data?.message || err.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default http
