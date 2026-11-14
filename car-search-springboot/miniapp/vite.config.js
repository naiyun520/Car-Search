import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  // 避免产物保留可选 catch 绑定等较新的语法，兼容微信工具的后续编译。
  build: { target: 'es2017' }
})
