package com.gamenuri.btscanner24.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google Play Billing for one non-consumable in-app product (#32). Ownership is cached on the
 * phone so Premium works offline; Play is the source of truth whenever it answers. No purchase or
 * device data goes anywhere else.
 */
class PlayPremiumStore(context: Context, private val productId: String = DEFAULT_PRODUCT_ID) : PremiumStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(PremiumState(isPremium = prefs.getBoolean(KEY_PREMIUM, false)))
    override val state: StateFlow<PremiumState> = _state.asStateFlow()

    private var product: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases.orEmpty())
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun refresh() {
        if (client.isReady) {
            loadProduct()
            queryOwned { }
            return
        }
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                    loadProduct()
                    queryOwned { }
                }

                override fun onBillingServiceDisconnected() {
                    // enableAutoServiceReconnection() reconnects on the next call.
                }
            },
        )
    }

    override fun purchase(activity: Activity): Boolean {
        val details = product ?: run {
            refresh()
            return false
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override suspend fun restore(): RestoreResult {
        if (!client.isReady) refresh()
        return suspendCancellableCoroutine { continuation ->
            queryOwned { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val found =
                details.productDetailsList.firstOrNull { it.productId == productId } ?: return@queryProductDetailsAsync
            product = found
            @Suppress("DEPRECATION") // single offer; the list API adds nothing for one lifetime product
            val price = found.oneTimePurchaseOfferDetails?.formattedPrice
            _state.update { it.copy(formattedPrice = price) }
        }
    }

    private fun queryOwned(onDone: (RestoreResult) -> Unit) {
        if (!client.isReady) {
            onDone(RestoreResult.FAILED)
            return
        }
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onDone(RestoreResult.FAILED)
                return@queryPurchasesAsync
            }
            val owned = handle(purchases)
            onDone(if (owned) RestoreResult.RESTORED else RestoreResult.NOTHING_TO_RESTORE)
        }
    }

    /** Applies Play's answer; returns whether the user owns Premium. Refunds turn it off again. */
    private fun handle(purchases: List<Purchase>): Boolean {
        val ours = purchases.filter { productId in it.products }
        val purchased = ours.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = ours.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        // Unacknowledged purchases are refunded by Play after 3 days.
        if (purchased != null && !purchased.isAcknowledged) {
            val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchased.purchaseToken).build()
            client.acknowledgePurchase(ack) { }
        }
        val isPremium = purchased != null
        prefs.edit().putBoolean(KEY_PREMIUM, isPremium).apply()
        _state.update { it.copy(isPremium = isPremium, isPending = pending && !isPremium) }
        return isPremium
    }

    companion object {
        /** Created in Play Console as a one-time product (US$4.99 base price). */
        const val DEFAULT_PRODUCT_ID = "premium_lifetime"

        private const val PREFS = "premium"
        private const val KEY_PREMIUM = "is_premium"
    }
}
