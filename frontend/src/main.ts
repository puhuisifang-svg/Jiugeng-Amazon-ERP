import { createApp } from 'vue'

import App from './App.vue'
import { runtimeConfig } from './config/runtime'
import router from './router'
import './styles/main.css'

createApp(App).provide('runtimeConfig', runtimeConfig).use(router).mount('#app')
