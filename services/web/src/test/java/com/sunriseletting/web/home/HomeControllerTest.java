package com.sunriseletting.web.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.util.List;

import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.security.SecurityConfiguration;
import com.sunriseletting.web.security.SessionPrincipal;
import com.sunriseletting.web.testsupport.Html;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(HomeController.class)
@Import(SecurityConfiguration.class)
class HomeControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void homePageIsRenderedAsHtml() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).as("home page should load").hasStatusOk();
        assertThat(result).as("home page should be HTML").hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
        assertThat(result).as("home page should render the home view").hasViewName("home");
    }

    @Test
    void homePageIdentifiesItselfAsTheSunriseLettingHomePage() {
        Document page = homePage("/");

        assertThat(page.title()).as("home page title should follow '{Page} - Sunrise Letting'")
                .isEqualTo("Home - Sunrise Letting");
        assertThat(page.select("main h1").text()).as("home page should have a heading naming Sunrise Letting")
                .isEqualTo("Sunrise Letting");
    }

    @Test
    void layoutSetsTheDocumentHead() {
        Document page = homePage("/");

        assertThat(page.select("html").attr("lang")).as("layout should set the language to British English")
                .isEqualTo("en-GB");
        assertThat(page.select("meta[name=viewport]").attr("content")).as("layout should set the viewport for mobile")
                .isEqualTo("width=device-width, initial-scale=1");
        assertThat(page.select("head link[rel=stylesheet]").attr("href")).as("layout should link the site stylesheet")
                .isEqualTo("/css/site.css");
    }

    @Test
    void layoutLoadsHtmxDeferredFromTheWebJar() {
        Document page = homePage("/");

        Element htmx = page.selectFirst("head script[src=/webjars/htmx.org/dist/htmx.min.js]");

        assertThat(htmx).as("layout should load HTMX from the version-less WebJar path").isNotNull();
        assertThat(htmx.hasAttr("defer")).as("HTMX should be deferred").isTrue();
    }

    @Test
    void layoutTellsHtmxToSwapErrorResponsesButNot204() {
        Document page = homePage("/");

        String config = page.select("meta[name=htmx-config]").attr("content");

        assertThat(config).as("4xx and 5xx responses should still be swapped")
                .contains("\"code\":\"[45]..\",\"swap\":true");
        assertThat(config).as("204 responses should leave the page unchanged")
                .contains("\"code\":\"204\",\"swap\":false");
    }

    @Test
    void layoutHasNoInlineScript() {
        Document page = homePage("/");

        assertThat(page.select("script:not([src])")).as("every script should come from a file").isEmpty();
    }

    @Test
    void skipLinkIsTheFirstFocusableElementAndPointsToMain() {
        Document page = homePage("/");

        Element first = page.body().selectFirst("a[href], button, input, select, textarea");

        assertThat(first.hasClass("skip-link")).as("the skip link should be the first focusable element").isTrue();
        assertThat(first.attr("href")).as("the skip link should go to the main content").isEqualTo("#main-content");
        assertThat(first.text()).as("the skip link should say what it does").isEqualTo("Skip to main content");
        assertThat(page.select("main#main-content[tabindex=-1]")).as("main should be able to take focus").hasSize(1);
    }

    @Test
    void headerLinksTheSiteNameToTheHomePage() {
        Document page = homePage("/");

        Element siteName = page.selectFirst("header.site-header a");

        assertThat(siteName.attr("href")).as("the site name should link to the home page").isEqualTo("/");
        assertThat(siteName.text()).as("the site name should be text").isEqualTo("Sunrise Letting");
    }

    @Test
    void headerNavHasTheSignedOutItemsInOrderWithNoReturnAddress() {
        for (String uri : List.of("/", "/?returnTo=/properties/123")) {
            Document page = homePage(uri);

            List<String> labels = page.select("header nav[aria-label=Main] ul li a").eachText();
            List<String> hrefs = page.select("header nav[aria-label=Main] ul li a").eachAttr("href");

            assertThat(labels).as("signed-out items should be in the design system's order on " + uri)
                    .containsExactly("Properties to let", "Sign in", "Create account");
            assertThat(hrefs).as("header links should carry no query string on " + uri)
                    .containsExactly("/properties", "/sign-in", "/create-account");
        }
    }

    @Test
    void signedInHeaderOnTheHomePageHasNoSignInOrCreateAccount() {
        Authentication visitor = UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-1", AccountRole.VISITOR, false), null,
                List.of(new SimpleGrantedAuthority("ROLE_VISITOR")));

        Document page = Html.page(mvc.get().uri("/").with(authentication(visitor)).exchange());

        assertThat(page.select("header nav li").eachText()).as("the Visitor's items, in order").containsExactly("Properties to let", "Sign out");
        assertThat(page.select("header form.sign-out[action=/sign-out][method=post] input[name=_csrf]")).as("the form carries a token").hasSize(1);
        assertThat(page.select("a[href=/sign-in], a[href=/create-account]")).as("those links aren't rendered at all").isEmpty();
    }

    @Test
    void headerMarksNoItemAsCurrentOnTheHomePage() {
        Document page = homePage("/");

        assertThat(page.select("header nav [aria-current]")).as("the home page isn't one of the nav's sections").isEmpty();
    }

    @Test
    void footerShowsOnlyTheCopyrightLine() {
        Document page = homePage("/");

        assertThat(page.select("footer.site-footer").text()).as("footer should show only the copyright line")
                .isEqualTo("© Sunrise Letting");
    }

    @Test
    void siteStylesheetIsServed() {
        MvcTestResult result = mvc.get().uri("/css/site.css").exchange();

        assertThat(result).as("the site stylesheet should be served").hasStatusOk();
        assertThat(result).as("the stylesheet should be served as CSS")
                .hasContentTypeCompatibleWith(MediaType.valueOf("text/css"));
        assertThat(result).bodyText().as("the stylesheet should define the design tokens on :root")
                .contains(":root").contains("--colour-primary: #8a3a00");
    }

    private Document homePage(String uri) {
        MvcTestResult result = mvc.get().uri(uri).exchange();
        assertThat(result).as("page at " + uri + " should load").hasStatusOk();
        return Html.page(result);
    }
}
