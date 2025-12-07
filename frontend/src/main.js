import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { wagmiAdapter } from './config/appkit'

const app = createApp(App)

app.use(VueQueryPlugin)
app.use(router)
app.mount('#app')
