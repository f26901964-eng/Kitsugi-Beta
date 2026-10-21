package com.kitsugi.animelist.data.account

import org.junit.Assert.*
import org.junit.Test

class AccountFormValidationTest {
    @Test fun validatesEmailsWithoutRejectingPlusAddressing() {
        assertTrue(AccountFormValidation.isValidEmail(" person+test@example.com "))
        for (invalid in listOf("", "@", "a@", "@example.com", "a b@example.com", "a@@example.com", "a@example")) {
            assertFalse(invalid, AccountFormValidation.isValidEmail(invalid))
        }
    }

    @Test fun newPasswordsMustBeLongEnoughAndMatchExactly() {
        assertFalse(AccountFormValidation.canRegister("a@example.com", "short", "short"))
        assertFalse(AccountFormValidation.canRegister("a@example.com", "password1", "password2"))
        assertFalse(AccountFormValidation.canRegister("a@example.com", "password1 ", "password1"))
        assertTrue(AccountFormValidation.canRegister("a@example.com", "password1 ", "password1 "))
        assertEquals(6, AccountFormValidation.MIN_SIGN_IN_PASSWORD) // Older accounts remain usable.
    }
}
