/**
 * Backend API utility for server-side requests
 */
export const useBackendFetch = () => {
    const config = useRuntimeConfig()
    const backendUrl = config.backendUrl || 'http://localhost:8080'

    return async <T>(endpoint: string, options?: RequestInit): Promise<T> => {
        const url = endpoint.startsWith('/')
            ? `${backendUrl}${endpoint}`
            : `${backendUrl}/${endpoint}`

        const response = await $fetch<T>(url, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...options?.headers,
            },
        })

        return response
    }
}

/**
 * Helper to get backend URL in server routes
 */
export const getBackendUrl = (): string => {
    const config = useRuntimeConfig()
    return config.backendUrl || 'http://localhost:8080'
}
