import type { RouteLocationNormalized } from 'vue-router'

/**
 * Auth middleware - protects admin routes
 */
export default defineNuxtRouteMiddleware((to: RouteLocationNormalized, _from: RouteLocationNormalized) => {
    const { isAdmin } = useAuth()

    if (!isAdmin()) {
        console.warn('Access denied: Admin role required')
        return navigateTo('/')
    }
})
