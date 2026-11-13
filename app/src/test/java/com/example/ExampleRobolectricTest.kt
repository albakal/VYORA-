package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.SupabaseLedgerClient
import com.example.media.ApkExportHelper
import com.example.media.formatCompactCount
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VYORA", appName)
    assertTrue(context.getString(R.string.supabase_ledger_title).contains("Supabase"))
    assertTrue(context.getString(R.string.save_apk_button).contains("APK"))
  }

  @Test
  fun `arabic localization includes supabase ledger and apk strings`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val arLocale = Locale.forLanguageTag("ar")
    val config = Configuration(context.resources.configuration).apply {
      setLocale(arLocale)
      setLayoutDirection(arLocale)
    }
    val arRes = context.createConfigurationContext(config).resources
    assertTrue(arRes.getString(R.string.supabase_ledger_title).contains("Supabase"))
    assertTrue(arRes.getString(R.string.save_apk_button).contains("APK"))
  }

  @Test
  fun `apk export filename and compact counts use stable format`() {
    assertTrue(ApkExportHelper.defaultApkFileName().endsWith(".apk"))
    assertEquals("1.5M", formatCompactCount(1_500_000L))
    assertEquals("2.3K", formatCompactCount(2_300L))
    assertEquals("999", formatCompactCount(999L))
  }

  @Test
  fun `supabase ledger client safely handles placeholder credentials`() = runBlocking {
    val client = SupabaseLedgerClient()
    if (!client.isClientConfigured) {
      val diag = client.checkPublicCatalogConnectivity()
      assertFalse(diag.clientKeysConfigured)
      assertFalse(diag.restReachable)
    }
  }
}
