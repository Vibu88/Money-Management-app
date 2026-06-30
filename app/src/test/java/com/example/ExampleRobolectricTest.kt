package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.FinanceViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Money Manager", appName)
  }

  @Test
  fun `test user registration and authentication lifecycle`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = FinanceViewModel(application)

    // Initially unregistered and logged out
    viewModel.resetUserSession()
    assertFalse(viewModel.isUserRegistered.value)
    assertFalse(viewModel.isUserLoggedIn.value)
    assertNull(viewModel.registeredUsername.value)

    // Register user
    val regSuccess = viewModel.registerUser("Alice", "secret123")
    assertTrue(regSuccess)
    assertTrue(viewModel.isUserRegistered.value)
    assertTrue(viewModel.isUserLoggedIn.value)
    assertEquals("Alice", viewModel.registeredUsername.value)

    // Log out
    viewModel.logoutUser()
    assertFalse(viewModel.isUserLoggedIn.value)
    assertTrue(viewModel.isUserRegistered.value) // remains registered

    // Login with incorrect password
    val failLogin = viewModel.loginUser("wrong_pass")
    assertFalse(failLogin)
    assertFalse(viewModel.isUserLoggedIn.value)

    // Login with correct password
    val correctLogin = viewModel.loginUser("secret123")
    assertTrue(correctLogin)
    assertTrue(viewModel.isUserLoggedIn.value)

    // Reset user session
    viewModel.resetUserSession()
    assertFalse(viewModel.isUserRegistered.value)
    assertFalse(viewModel.isUserLoggedIn.value)
    assertNull(viewModel.registeredUsername.value)
  }
}
