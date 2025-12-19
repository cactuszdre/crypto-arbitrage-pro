/**
 * Composable SEO pour gérer les balises SEO dynamiques
 * Utilisé pour ajouter la canonical URL dynamique à chaque page
 */
export const useSeo = () => {
    const route = useRoute()
    const baseUrl = 'https://cactusfund.app'

    /**
     * Configure les balises SEO de base avec canonical dynamique
     * @param options - Options SEO (title, description, etc.)
     */
    const setSeo = (options: {
        title: string
        description: string
        ogTitle?: string
        ogDescription?: string
        ogImage?: string
        noindex?: boolean
    }) => {
        // Déterminer le path canonique (sans query params)
        const canonicalPath = route.path === '/' ? '' : route.path
        const canonicalUrl = `${baseUrl}${canonicalPath}`

        // Meta tags via useSeoMeta
        useSeoMeta({
            title: options.title,
            description: options.description,
            ogTitle: options.ogTitle || options.title,
            ogDescription: options.ogDescription || options.description,
            ogUrl: canonicalUrl,
            ogImage: options.ogImage || `${baseUrl}/logo_cactus_without-bg.png`,
            twitterCard: 'summary_large_image',
            robots: options.noindex ? 'noindex, nofollow' : 'index, follow',
        })

        // Canonical link via useHead
        useHead({
            link: [
                { rel: 'canonical', href: canonicalUrl }
            ]
        })
    }

    return {
        setSeo,
        baseUrl
    }
}
