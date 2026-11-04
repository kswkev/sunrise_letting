package com.sunriseletting.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReturnAddressTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/properties/123",
            "/properties/123/viewing",
            "/",
            "/properties?bedrooms=2&location=Leeds",
            "/properties?from=https://example.com",
            "/a%20b" })
    void pathsOnThisSiteAreAccepted(String value) {
        assertEquals(value, ReturnAddress.validOrNull(value), value + " is a path on this site");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "https://evil.example",
            "http://evil.example/properties",
            "//evil.example",
            "/\\evil.example",
            "\\\\evil.example",
            "/a\\b",
            "javascript:alert(1)",
            "properties/123",
            "/a%0d%0aSet-Cookie:x=y",
            "/a%0D%0ASet-Cookie:x=y",
            "/a%00b",
            "/a%7Fb",
            "/a\rb",
            "/a\nb",
            "/a\tb",
            "/a\u0000b",
            "/a\u007Fb",
            "/%5Cevil.example",
            "/%2Fevil.example",
            "/%2f/evil.example" })
    void anythingElseCountsAsNoReturnAddress(String value) {
        assertNull(ReturnAddress.validOrNull(value), "this isn't a path on this site: " + value);
    }
}
