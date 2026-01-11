/**
 * Auth middleware - protects admin routes
 * Redirects to home if user is not authenticated as admin
 */
export default defineNuxtRouteMiddleware((to, from) => {
    // Only run on client-side (localStorage is not available on server)
    if (import.meta.server) {
        return
    }

    const { isAdmin, isAuthenticated, token } = useAuth()
    const { hasValidToken } = useSecureAuth()

    // Check if user has a valid token and is admin
    const hasToken = hasValidToken.value || !!token.value
    const isUserAdmin = isAdmin.value

    // If not authenticated or not admin, redirect to home
    if (!hasToken) {
        console.warn('[Auth Middleware] No valid token, redirecting to home')
        return navigateTo('/')
    }

    if (!isUserAdmin) {
        console.warn('[Auth Middleware] User is not admin, redirecting to home')
        return navigateTo('/')
    }
})
