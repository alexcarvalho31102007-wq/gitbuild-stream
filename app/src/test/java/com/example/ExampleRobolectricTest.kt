package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.storage.TokenStorage
import com.example.data.zip.ZipProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GitCompile", appName)
    }

    @Test
    fun `token storage stores and clears token correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = TokenStorage(context)
        storage.token = "ghp_test_token_123"
        assertEquals("ghp_test_token_123", storage.token)
        storage.clear()
        assertEquals("", storage.token)
    }

    @Test
    fun `universal workflow contains required steps`() {
        val workflow = ZipProcessor.generateUniversalWorkflow()
        assertTrue(workflow.contains("actions/checkout@v4"))
        assertTrue(workflow.contains("Inspecionar e Detectar Projeto"))
        assertTrue(workflow.contains("Executar Compilação"))
        assertTrue(workflow.contains("upload-artifact@v4"))
    }
}
