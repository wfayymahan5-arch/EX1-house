package com.ex1legend.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAdminAccessTest {
    @Test fun ownerRetainsAccessEvenWithoutExplicitDelegatedPermissions() {
        assertTrue(hasNativeAdminAccess(isOwner = true, permissions = emptyList()))
    }

    @Test fun delegatedManagerIsAllowedOnlyWithServerIssuedPermissions() {
        assertTrue(hasNativeAdminAccess(isOwner = false, permissions = listOf("teams.view")))
        assertFalse(hasNativeAdminAccess(isOwner = false, permissions = emptyList()))
    }

    @Test fun emptyPermissionStringsDoNotGrantAccess() {
        assertTrue(hasNativeAdminAccess(isOwner = false, permissions = listOf("")))
    }
}
