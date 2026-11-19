package com.amirmalik.tasbeeh

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun hasValidatedInternetConnection(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return@runCatching false
    val activeNetwork = manager.activeNetwork ?: return@runCatching false
    val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return@runCatching false
    hasValidatedInternetConnection(capabilities)
}.getOrDefault(false)

internal fun hasValidatedInternetConnection(capabilities: NetworkCapabilities?): Boolean =
    capabilities != null &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

internal fun networkFailureMessage(serviceName: String, error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    return when {
        causes.any { it is UnknownHostException } ->
            "No internet connection or DNS service is available. Connect to the internet and try again. Your saved content remains on this device."
        causes.any { it is SocketTimeoutException } ->
            "$serviceName took too long to respond. Check your connection and try again. Your saved content remains available."
        causes.any { it is ConnectException } ->
            "Could not connect to $serviceName. Check your internet connection and try again. Your saved content remains available."
        causes.any { it is IOException } ->
            "$serviceName could not be reached. Check your connection and try again. Your saved content remains available."
        else ->
            "$serviceName could not be updated. You can continue using content already saved on this device, or try again."
    }
}
