package com.sunriseletting.web.properties;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(PropertiesController.class)
@Import(SecurityConfiguration.class)
class PropertiesControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void pageHasTheTitleAndHeadingAndNoBanner() {
        MvcTestResult result = mvc.get().uri("/properties").exchange();

        assertThat(result).as("the page should load").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.title()).as("the title should name the page").isEqualTo("Properties to let - Sunrise Letting");
        assertThat(page.select("main h1").text()).as("the heading should name the page").isEqualTo("Properties to let");
        assertThat(page.select(".notification-banner")).as("no banner without the flash").isEmpty();
        assertThat(page.select("main > *")).as("main holds only the heading until #4").hasSize(1);
    }

    @Test
    void headerMarksPropertiesToLetAsCurrent() {
        MvcTestResult result = mvc.get().uri("/properties").exchange();

        assertThat(result).as("the page should load").hasStatusOk();
        assertThat(Html.page(result).select("header nav a[aria-current=page]").eachText())
                .as("only Properties to let should be current").containsExactly("Properties to let");
    }

    @Test
    void signedOutHeaderHasTheThreeSignedOutItems() {
        Document page = Html.page(mvc.get().uri("/properties").exchange());

        assertThat(page.select("header nav li").eachText()).as("the signed-out items, in order")
                .containsExactly("Properties to let", "Sign in", "Create account");
        assertThat(page.select("form.sign-out")).as("no Sign out when signed out").isEmpty();
    }

    @Test
    void signedInHeaderHasPropertiesToLetAndTheSignOutForm() {
        MvcTestResult result = mvc.get().uri("/properties").with(authentication(visitor())).exchange();

        assertThat(result).as("the page should load").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.select("header nav li").eachText()).as("the Visitor's items, in order").containsExactly("Properties to let", "Sign out");
        assertThat(page.select("header nav a[aria-current=page]").eachText()).as("the current page is still marked").containsExactly("Properties to let");
        Element form = page.selectFirst("header nav li:last-child form.sign-out");
        assertThat(form.attr("method")).as("sign out is a POST").isEqualToIgnoringCase("post");
        assertThat(form.attr("action")).as("sign out posts to its endpoint").isEqualTo("/sign-out");
        assertThat(form.select("input[type=hidden][name=_csrf]").attr("value")).as("the form carries a CSRF token").isNotBlank();
        assertThat(form.select("button[type=submit].sign-out__button").text()).as("the button's text").isEqualTo("Sign out");
        assertThat(page.select("a[href=/sign-in]")).as("Sign in isn't rendered at all").isEmpty();
        assertThat(page.select("a[href=/create-account]")).as("Create account isn't rendered at all").isEmpty();
    }

    @Test
    void flashShowsTheSuccessBannerAboveTheHeading() {
        MvcTestResult result = mvc.get().uri("/properties").flashAttr(PropertiesController.ACCOUNT_CREATED, true).exchange();

        assertThat(result).as("the page should load").hasStatusOk();
        Document page = Html.page(result);
        Element banner = page.selectFirst("main > .notification-banner--success[role=status][autofocus]");
        assertThat(banner).as("the success banner should be at the top of main").isNotNull();
        assertThat(banner.select("h2").text()).as("the banner should have the agreed copy").isEqualTo("Your account has been created");
        assertThat(banner.nextElementSibling().tagName()).as("the banner should sit directly above the heading").isEqualTo("h1");
        assertThat(banner.elementSiblingIndex()).as("the banner should come first in main").isZero();
    }

    @Test
    void secondRequestWithoutTheFlashHasNoBanner() {
        mvc.get().uri("/properties").flashAttr(PropertiesController.ACCOUNT_CREATED, true).exchange();

        MvcTestResult second = mvc.get().uri("/properties").exchange();

        assertThat(second).as("the page should load").hasStatusOk();
        assertThat(Html.page(second).select(".notification-banner")).as("the banner should be shown only once").isEmpty();
    }

    private Authentication visitor() {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal("account-1", AccountRole.VISITOR, false), null,
                List.of(new SimpleGrantedAuthority("ROLE_VISITOR")));
    }
}
