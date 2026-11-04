package com.sunriseletting.web.testsupport;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Parses what a page or fragment test got back, so tests can assert with CSS selectors. */
public final class Html {

    private Html() {
    }

    /** The response body as UTF-8 text. */
    public static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A whole page. */
    public static Document page(MvcTestResult result) {
        return Jsoup.parse(body(result));
    }

    /** A fragment with no layout: its top-level elements are the children of {@code body()}. */
    public static Document fragment(MvcTestResult result) {
        return Jsoup.parseBodyFragment(body(result));
    }
}
