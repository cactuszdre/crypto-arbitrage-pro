import { createWalletClient, custom } from 'viem'
import { arbitrum } from 'viem/chains'
import { ExchangeClient, InfoClient, HttpTransport } from '@nktkas/hyperliquid'

export const executeHyperliquidOrder = async (walletProvider, orderDetails) => {
    if (!walletProvider) throw new Error("Wallet not connected")

    const symbol = orderDetails.symbol // e.g. "ETH"
    const sizeUsd = parseFloat(orderDetails.size)
    const isBuy = orderDetails.isBuy

    // 1. Setup Viem Client
    const client = createWalletClient({
        chain: arbitrum,
        transport: custom(walletProvider)
    })
    const [account] = await client.getAddresses()

    console.log(`SDK Execution: Initializing for ${symbol} order. Account: ${account}`)

    // 2. Transports & Clients
    const transport = new HttpTransport({
        url: 'https://api.hyperliquid.xyz',
        timeout: 10000
    })
    const exchange = new ExchangeClient({
        wallet: {
            address: account,
            async getAddress() { return account },
            async signTypedData(domain, types, value) {
                const { EIP712Domain, ...validTypes } = types
                return await client.signTypedData({
                    account, domain, types: validTypes, primaryType: Object.keys(validTypes)[0], message: value
                })
            }
        },
        transport,
    })
    const info = new InfoClient({ transport })

    // 3. Fetch Metadata (Universe) & Prices (Mids) in parallel
    console.log("Fetching Market Data...")
    const [meta, mids] = await Promise.all([
        info.meta(),
        info.allMids()
    ])

    // 4. Find Asset ID & Metadata
    // The 'universe' array index IS the asset ID (in JS SDK context usually, or we search)
    // Actually, universe is array of info. Asset ID is the index.
    let assetIndex = -1
    let assetInfo = null

    for (let i = 0; i < meta.universe.length; i++) {
        if (meta.universe[i].name === symbol) {
            assetIndex = i
            assetInfo = meta.universe[i]
            break
        }
    }

    if (assetIndex === -1) {
        throw new Error(`Symbol ${symbol} not found in Hyperliquid Universe`)
    }

    const szDecimals = assetInfo.szDecimals
    console.log(`Asset Found: ${symbol} (ID: ${assetIndex}, Decimals: ${szDecimals})`)

    // 5. Get Market Price
    // mids is object { "ETH": "3000.50", "BTC": ... } or similar depending on SDK.
    // Let's assume it matches symbol.
    const rawPrice = mids[symbol]
    if (!rawPrice) {
        throw new Error(`Could not fetch price for ${symbol}`)
    }
    const currentPrice = parseFloat(rawPrice)
    console.log(`Current Market Price for ${symbol}: ${currentPrice}`)

    // 6. Calculate Execution Price (Market Order Emulation)
    // 5% slippage to ensure fill
    const slippage = 0.05
    const executionPrice = isBuy ? currentPrice * (1 + slippage) : currentPrice * (1 - slippage)

    // Hyperliquid Price Rules: Generally 5 significant figures.
    // e.g. 3047.55 -> 3047.6 (5 digits)
    // We use toPrecision(5) and convert back to string to avoid scientific notation for small numbers.
    const formattedPrice = parseFloat(executionPrice.toPrecision(5)).toString()

    // 7. Calculate Size in Tokens
    const sizeTokens = sizeUsd / currentPrice
    const roundedSize = sizeTokens.toFixed(szDecimals)

    if (parseFloat(roundedSize) === 0) {
        throw new Error(`Calculated size is too small for this asset. Min size not met with $${sizeUsd} at price ${currentPrice}`)
    }

    console.log("Preparing Market Order:", {
        symbol,
        isBuy,
        currentPrice,
        executionPrice: formattedPrice,
        sizeUsd,
        sizeTokens,
        roundedSize
    })

    // 8. Send Order
    try {
        const result = await exchange.order({
            orders: [{
                a: assetIndex,
                b: isBuy,
                p: formattedPrice,
                s: roundedSize,
                r: false,
                t: { limit: { tif: 'Ioc' } } // Immediate or Cancel (Market-like)
            }],
            grouping: 'na'
        })

        console.log("SDK Response:", result)

        if (result.status === 'err') {
            throw new Error("Hyperliquid API Error: " + JSON.stringify(result.response))
        }

        return {
            success: true,
            message: `Market Order Placed! ${symbol} @ ~${currentPrice}`,
            data: result,
            signature: "Signed via SDK"
        }

    } catch (error) {
        console.error("SDK Execution Failed:", error)
        throw error
    }
}
