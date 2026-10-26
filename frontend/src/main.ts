import { createApp } from 'vue'

import App from './App.vue'
import { runtimeConfig } from './config/runtime'
import router from './router'
import { session } from './state/session'
import './styles/main.css'
import './styles/uiConsistency.css'

async function bootstrap() {
  await session.initialize()
  createApp(App).provide('runtimeConfig', runtimeConfig).use(router).mount('#app')
}

void bootstrap()
