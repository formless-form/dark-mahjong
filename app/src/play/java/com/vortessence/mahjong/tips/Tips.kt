package com.vortessence.mahjong.tips

import androidx.activity.ComponentActivity
import com.vortessence.mahjong.BuildConfig
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional tips through Google Play Billing: three consumable products, so a player can tip as
 * often as they like. Google handles the payment; the game only learns that a tip went through,
 * and thanks them. Nothing in the game depends on it.
 *
 * Settings always offers them in this build; the card explains when Google Play can't price them
 * yet (offline, or Play still starting up).
 */
class Tips(private val activity: ComponentActivity) : DefaultLifecycleObserver {
    private val state = MutableStateFlow<List<TipOffer>>(emptyList())
    val offers: StateFlow<List<TipOffer>> = state.asStateFlow()

    private val thanks = MutableStateFlow(false)
    val thanked: StateFlow<Boolean> = thanks.asStateFlow()

    private var details: Map<String, ProductDetails> = emptyMap()

    // A payment sheet is open, so any paid tip that turns up is the player's tip and earns thanks
    private var awaitingTip = false

    // Purchases already being used up, so the payment result and the return to the game don't race
    private val consuming = mutableSetOf<String>()

    /** The Play build always offers tips; the card says so if Google Play can't price them yet. */
    val available = true

    private val client: BillingClient = BillingClient.newBuilder(activity)
        .setListener { result, purchases ->
            // Thanked the moment Google Play says the tip went through (or is on its way), without
            // waiting for it to be used up: that step can lose a race with the return to the game
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.orEmpty().forEach { purchase ->
                    if (purchase.purchaseState != Purchase.PurchaseState.UNSPECIFIED_STATE) thanks.value = true
                    consume(purchase)
                }
            }
            awaitingTip = false
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        activity.lifecycle.addObserver(this)
        connect()
    }

    override fun onResume(owner: LifecycleOwner) {
        // A tip paid while away (a pending payment that cleared) is finished off quietly
        if (client.isReady) finishPaid()
    }

    override fun onDestroy(owner: LifecycleOwner) = client.endConnection()

    /** Opens Google Play's own payment sheet for [offer]. */
    fun tip(offer: TipOffer) {
        val product = details[offer.productId] ?: run {
            // Test builds only: a sample tip just shows the thank-you, with no payment
            if (BuildConfig.PERF_HOOKS) thanks.value = true
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .apply { offerOf(product)?.offerToken?.let { setOfferToken(it) } }
                        .build(),
                ),
            )
            .build()
        awaitingTip = client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Called when the tip card opens: tries Google Play again if the prices haven't arrived. */
    fun refresh() {
        if (!client.isReady) connect() else if (details.isEmpty()) loadOffers()
    }

    fun clearThanks() {
        thanks.value = false
    }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return showSamples()
                loadOffers()
                finishPaid()
            }

            // Tried again the next time the app comes to the front
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun loadOffers() {
        val products = TIP_PRODUCTS.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, found ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync showSamples()
            details = found.productDetailsList.associateBy { it.productId }
            state.value = TIP_PRODUCTS.mapNotNull { id ->
                val price = details[id]?.let(::offerOf)?.formattedPrice ?: return@mapNotNull null
                TipOffer(id, price)
            }
            if (state.value.isEmpty()) showSamples()
        }
    }

    /**
     * A tip's price. Products made in today's Play Console list their prices as purchase options and
     * leave the older single offer empty unless one is marked backwards compatible, so the list is
     * read first.
     */
    private fun offerOf(product: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? =
        product.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: product.oneTimePurchaseOfferDetails

    /**
     * Test builds only (BuildConfig.PERF_HOOKS, set by tools/deploy-to-phone.sh): sample prices, so the
     * tip card can be tried before Google Play has the products. Store builds never show these.
     */
    private fun showSamples() {
        if (BuildConfig.PERF_HOOKS) state.value = TIP_PRODUCTS.zip(listOf("$0.50", "$1.00", "$2.00")) { id, price -> TipOffer(id, price) }
    }

    // Any tip paid but not yet used up: consumed, so the same tip can be left again
    private fun finishPaid() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            purchases.forEach { purchase ->
                // Back from the payment sheet before its result arrived: still the player's tip
                if (awaitingTip && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) thanks.value = true
                consume(purchase)
            }
        }
    }

    // Uses up a paid tip, so the same tip can be left again (a pending one waits until it's paid)
    private fun consume(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val token = purchase.purchaseToken
        if (!consuming.add(token)) return
        client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build()) { result, _ ->
            // Tried again next time if it didn't go through
            if (result.responseCode != BillingClient.BillingResponseCode.OK) consuming.remove(token)
        }
    }
}
