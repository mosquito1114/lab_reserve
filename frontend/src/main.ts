import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import 'element-plus/dist/index.css'
import '@/styles/index.scss'

import App from './App.vue'
import router from './router'
import PageHeader from '@/components/PageHeader.vue'

const app = createApp(App)

// 全局注册 Element Plus 图标，模板里可直接写 <el-icon><Search /></el-icon>
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

// 全局注册页面标题组件，26 个业务页不用各自 import
app.component('PageHeader', PageHeader)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
