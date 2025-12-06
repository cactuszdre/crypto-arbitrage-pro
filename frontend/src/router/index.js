import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/Login.vue')
    },
    {
        path: '/',
        name: 'Dashboard',
        component: Dashboard,
        meta: { requiresAuth: true }
    },
    {
        path: '/history',
        name: 'History',
        component: () => import('../views/History.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/arbitrage',
        name: 'Arbitrage',
        component: () => import('../views/Arbitrage.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/admin',
        name: 'Admin',
        component: () => import('../views/Admin.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
        path: '/:pathMatch(.*)*',
        redirect: '/'
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    const user = localStorage.getItem('user')
    if (to.meta.requiresAuth && !user) {
        next('/login')
    } else {
        next()
    }
})

export default router
