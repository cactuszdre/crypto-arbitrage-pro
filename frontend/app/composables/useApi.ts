/**
 * Composable for arbitrage data fetching using Nuxt's useFetch
 * This is the Nuxt-native way to fetch data with SSR support
 */

// Fetch all DEX configurations
export const useDexList = () => {
    return useFetch('/api/dex', {
        key: 'dex-list',
        default: () => []
    })
}

// Fetch all arbitrage rates
export const useArbitrageRates = () => {
    return useFetch('/api/opportunites/rates', {
        key: 'arbitrage-rates',
        default: () => []
    })
}

// Fetch funding rates
export const useFundingRates = () => {
    return useFetch('/api/funding-rates', {
        key: 'funding-rates',
        default: () => []
    })
}

// Fetch symbols
export const useSymbols = () => {
    return useFetch('/api/funding-rates/symbols', {
        key: 'symbols',
        default: () => []
    })
}

// Fetch chart comparison data
export const useChartComparison = (symbol: Ref<string>, hours: Ref<number>) => {
    return useFetch('/api/charts/comparison', {
        key: () => `chart-comparison-${symbol.value}-${hours.value}`,
        query: { symbol, hours },
        watch: [symbol, hours]
    })
}

// Fetch hourly chart data
export const useChartHourly = (symbol: Ref<string>, hours: Ref<number>) => {
    return useFetch('/api/charts/comparison-hourly', {
        key: () => `chart-hourly-${symbol.value}-${hours.value}`,
        query: { symbol, hours },
        watch: [symbol, hours]
    })
}

// Fetch daily chart data
export const useChartDaily = (symbol: Ref<string>, days: Ref<number>) => {
    return useFetch('/api/charts/comparison-daily', {
        key: () => `chart-daily-${symbol.value}-${days.value}`,
        query: { symbol, days },
        watch: [symbol, days]
    })
}

// Send chat message
export const useChatMessage = () => {
    const pending = ref(false)
    const error = ref<Error | null>(null)

    const sendMessage = async (messages: any[]) => {
        pending.value = true
        error.value = null

        try {
            const response = await $fetch('/api/chat', {
                method: 'POST',
                body: messages
            })
            return response
        } catch (e) {
            error.value = e as Error
            throw e
        } finally {
            pending.value = false
        }
    }

    return { sendMessage, pending, error }
}

// Fetch user by wallet
export const useUserByWallet = (address: Ref<string | null>) => {
    return useFetch(() => address.value ? `/api/users/wallet/${address.value}` : null, {
        key: () => `user-${address.value}`,
        watch: [address],
        immediate: false
    })
}

// Update user login
export const useUpdateUserLogin = () => {
    const pending = ref(false)

    const updateLogin = async (address: string) => {
        pending.value = true
        try {
            await $fetch(`/api/users/wallet/${address}/login`, {
                method: 'POST'
            })
        } finally {
            pending.value = false
        }
    }

    return { updateLogin, pending }
}
