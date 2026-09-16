package com.aurora.player.location

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Najlepsza-próba: ostatnia znana lokalizacja + [Geocoder] → kod kraju ISO — DESIGN.md Etap 31/33
 * (współdzielone między Radiem i regionalnymi sugestiami Podcastów, stąd osobny, neutralny
 * pakiet zamiast trzymania tego w `radio`). Zwraca `null` nie tylko przy odmowie zgody, ale też
 * przy braku ostatniej lokalizacji lub braku działającego Geocodera (częste na
 * emulatorach/niektórych ROM-ach bez usług Google) — wołający ma ZAWSZE pokazać ręczny wybór
 * kraju jako fallback, nie tylko po odmowie permission.
 */
suspend fun resolveCountryCodeFromLastKnownLocation(context: Context): String? =
    withContext(Dispatchers.IO) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext null
            var best: Location? = null
            for (provider in locationManager.getProviders(true)) {
                val candidate = locationManager.getLastKnownLocation(provider) ?: continue
                if (best == null || candidate.accuracy < best.accuracy) best = candidate
            }
            val location = best ?: return@withContext null
            if (!Geocoder.isPresent()) return@withContext null
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
            addresses?.firstOrNull()?.countryCode
        } catch (e: Exception) {
            null
        }
    }
