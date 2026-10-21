package com.kitsugi.animelist.data.account

/** No Android dependency: the same rules cover the form and repository boundary. */
object AccountFormValidation {
    const val MIN_SIGN_IN_PASSWORD = 6 // Keep existing Supabase accounts usable.
    const val MIN_NEW_PASSWORD = 8
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isValidEmail(email: String): Boolean = email.length <= 254 && emailPattern.matches(email.trim())

    fun canRegister(email: String, password: String, confirmation: String): Boolean =
        isValidEmail(email) && password.length >= MIN_NEW_PASSWORD && password == confirmation
}
