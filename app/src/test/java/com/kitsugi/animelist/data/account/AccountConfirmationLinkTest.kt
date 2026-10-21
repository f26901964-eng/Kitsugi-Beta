package com.kitsugi.animelist.data.account

import org.junit.Assert.*
import org.junit.Test

class AccountConfirmationLinkTest {
    @Test fun acceptsOnlyTheExactMobileCallbackAndOnePkceCode() {
        assertEquals(AccountConfirmationLink.Result.Code("test-code"), AccountConfirmationLink.parse("kitsugi://account-confirm?code=test-code"))
        assertEquals(AccountConfirmationLink.Result.Code("a+b"), AccountConfirmationLink.parse("kitsugi://account-confirm/?code=a%2Bb"))
    }

    @Test fun bearerTokensAreNeverAutomaticallyImported() {
        assertEquals(AccountConfirmationLink.Result.ManualSignIn,
            AccountConfirmationLink.parse("kitsugi://account-confirm#access_token=test-token&refresh_token=test-refresh"))
        assertEquals(AccountConfirmationLink.Result.ManualSignIn, AccountConfirmationLink.parse("kitsugi://account-confirm"))
    }

    @Test fun rejectsErrorsAmbiguousCodesAndMalformedLinks() {
        for (url in listOf(
            "kitsugi://account-confirm?code=one&code=two",
            "kitsugi://account-confirm?code=",
            "kitsugi://account-confirm?code=%20",
            "kitsugi://account-confirm?code=%XX",
            "kitsugi://account-confirm?code=one&error=expired",
            "kitsugi://account-confirm#error=access_denied",
            "kitsugi://user@account-confirm?code=one",
            "kitsugi://account-confirm:99?code=one",
            "kitsugi://account-confirm/unexpected?code=one"
        )) assertEquals(url, AccountConfirmationLink.Result.Invalid, AccountConfirmationLink.parse(url))
    }

    @Test fun ignoresUnrelatedServiceLinks() {
        for (url in listOf("malapp://auth?code=one", "https://account-confirm?code=one", "kitsugi://account-confirm.example?code=one")) {
            assertEquals(AccountConfirmationLink.Result.NotAccountLink, AccountConfirmationLink.parse(url))
        }
    }
}
