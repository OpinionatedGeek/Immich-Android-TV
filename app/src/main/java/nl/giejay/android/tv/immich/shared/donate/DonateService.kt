package nl.giejay.android.tv.immich.shared.donate

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.ImmichApplication
import timber.log.Timber

class DonateService(private val context: Context) : PurchasesUpdatedListener {
    private lateinit var mBillingClient: BillingClient
    private val mainScope = CoroutineScope(Job() + Dispatchers.Main)

    fun setupBilling(callback: (Boolean) -> Unit) {
        mBillingClient =
            BillingClient.newBuilder(context).setListener(this).enablePendingPurchases().build()
        mBillingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    callback(true)
                } else {
                    Timber.e(billingResult.debugMessage)
                    callback(false)
                }
            }

            override fun onBillingServiceDisconnected() {
            }
        })
    }

    fun getProducts(callback: (List<ProductDetails>) -> Unit) {
        getNonPurchasedProducts { products ->
            if (products.isEmpty()) {
                callback(emptyList())
            } else {
                val params: QueryProductDetailsParams.Builder =
                    QueryProductDetailsParams.newBuilder()
                params.setProductList(products.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP).build()
                })
                mBillingClient.queryProductDetailsAsync(params.build()) { billingResult, list ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        callback(list)
                    } else {
                        Timber.e(billingResult.debugMessage)
                        callback(emptyList())
                    }
                }
            }
        }
    }

    fun launchBilling(activity: Activity, productDetails: ProductDetails) {
        val flowParams: BillingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails).build()
                )
            )
            .build()
        mBillingClient.launchBillingFlow(activity, flowParams)
    }

    private fun getNonPurchasedProducts(callback: (Set<String>) -> Unit) {
        mBillingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { _, list ->
            val boughtProducts = list.flatMap { it.products }.toSet()
            callback(PRODUCTS.subtract(boughtProducts))
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, list: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
            for (purchase in list) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams: AcknowledgePurchaseParams =
                            AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                        mBillingClient.acknowledgePurchase(
                            acknowledgePurchaseParams
                        ) { innerResult ->
                            if (innerResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                showToast(
                                    "Thanks for your donation, highly appreciated!",
                                    Toast.LENGTH_LONG
                                )
                            } else {
                                Timber.e(innerResult.debugMessage)
//                                showToast(
//                                    "Error while verifying your purchase, please contact the developer.",
//                                    Toast.LENGTH_SHORT
//                                )
                            }
                        }
                    }
                } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    showToast("Thanks for your donation, highly appreciated!", Toast.LENGTH_LONG)
                } else if (purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
                    showToast("Your donation could not be completed", Toast.LENGTH_SHORT)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
//            showToast("Donation cancelled", Toast.LENGTH_SHORT)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
            showToast(
                "Could not finalize donation due to error, please contact the developer.",
                Toast.LENGTH_SHORT
            )
        } else {
            showToast(
                "Could not finalize donation due to error, please contact the developer.",
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun showToast(message: String?, length: Int) {
        mainScope.launch {
            Toast.makeText(ImmichApplication.appContext, message, length).show()
        }
    }

    companion object {
        private const val PRODUCT_ID_1 = "thank_you"
        private const val PRODUCT_ID_2 = "buy_a_coffee"
        private const val PRODUCT_ID_3 = "buy_a_coffee_and_cake"
        private val PRODUCTS = listOf(PRODUCT_ID_1, PRODUCT_ID_2, PRODUCT_ID_3)
    }
}