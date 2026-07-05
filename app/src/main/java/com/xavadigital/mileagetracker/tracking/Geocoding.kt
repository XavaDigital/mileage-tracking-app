package com.xavadigital.mileagetracker.tracking

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object Geocoding {

    suspend fun addressFor(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            try {
                withTimeoutOrNull(5_000) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= 33) {
                        suspendCancellableCoroutine { cont ->
                            geocoder.getFromLocation(
                                lat, lng, 1,
                                object : Geocoder.GeocodeListener {
                                    override fun onGeocode(addresses: MutableList<Address>) {
                                        cont.resume(addresses.firstOrNull()?.toShortString())
                                    }

                                    override fun onError(errorMessage: String?) {
                                        cont.resume(null)
                                    }
                                }
                            )
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.toShortString()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun Address.toShortString(): String? {
        val street = listOfNotNull(thoroughfare).joinToString(" ")
        val area = subLocality ?: locality ?: adminArea
        val parts = listOf(street, area.orEmpty()).filter { it.isNotBlank() }
        return parts.joinToString(", ").ifBlank { null }
    }
}
