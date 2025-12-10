import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import { useAuth } from '../stores/auth'

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/Login.vue')
    },
    {
        path: '/',
        name: 'Dashboard',
        component: Dashboard
    },
    {
        path: '/arbitrage',
        name: 'Arbitrage',
        component: () => import('../views/Arbitrage.vue')
    },
    {
        path: '/swap',
        name: 'Swap',
        component: () => import('../views/Swap.vue')
    },
    {
        path: '/billing',
        name: 'Billing',
        component: () => import('../views/Billing.vue')
    },
    {
        path: '/learn',
        name: 'Learn',
        component: () => import('../views/FAQ.vue')
    },
    {
        path: '/admin',
        name: 'Admin',
        component: () => import('../views/Admin.vue'),
        meta: { requiresAdmin: true }
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

// Navigation guard to protect admin routes
router.beforeEach((to, from, next) => {
    const { isAdmin } = useAuth()

    if (to.meta.requiresAdmin && !isAdmin()) {
        // Redirect non-admin users to home
        console.warn('Access denied: Admin role required')
        next({ path: '/' })
    } else {
        next()
    }
})

export default router

