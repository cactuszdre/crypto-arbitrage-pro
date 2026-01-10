/**
 * Backend API utility for server-side requests - MICROSERVICES VERSION
 * Validated architecture:
 * - funding-service (8081): Raw data, DEXs, Funding Rates, Chat
 * - arbitrage-service (8082): Analytics, Charts, Arbitrage Opps
 */

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

interface FetchOptions {
    method?: HttpMethod
    body?: unknown
    headers?: Record<string, string>
}

/**
 * Get the correct microservice URL based on the endpoint
 */
const getServiceUrl = (endpoint: string): string => {
    const config = useRuntimeConfig()

    // Default URLs
    const fundingUrl = config.fundingServiceUrl || 'http://localhost:8081'
    const arbitrageUrl = config.arbitrageServiceUrl || 'http://localhost:8082'

    // Arbitrage Service Endpoints
    if (endpoint.includes('/api/arbitrage') ||
        endpoint.includes('/api/charts') ||
        endpoint.includes('/api/opportunites')) {
        return arbitrageUrl
    }

    // Default to Funding Service (dex, funding-rates, chat, etc.)
    return fundingUrl
}

export const useBackendFetch = () => {
    return async <T>(endpoint: string, options?: FetchOptions): Promise<T> => {
        const serviceUrl = getServiceUrl(endpoint)

        const url = endpoint.startsWith('/')
            ? `${serviceUrl}${endpoint}`
            : `${serviceUrl}/${endpoint}`

        const response = await $fetch<T>(url, {
            method: options?.method || 'GET',
            body: options?.body as Record<string, unknown> | undefined,
            headers: {
                'Content-Type': 'application/json',
                ...options?.headers,
            },
        })

        return response as T
    }
}

/**
 * Helper to get Funding Service URL
 */
export const getFundingServiceUrl = (): string => {
    const config = useRuntimeConfig()
    return config.fundingServiceUrl || 'http://localhost:8081'
}

/**
 * Helper to get Arbitrage Service URL
 */
export const getArbitrageServiceUrl = (): string => {
    const config = useRuntimeConfig()
    return config.arbitrageServiceUrl || 'http://localhost:8082'
}

/**
 * @deprecated Use specific service URL helpers
 */
export const getBackendUrl = (): string => {
    return getFundingServiceUrl()
}
