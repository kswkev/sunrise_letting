package com.sunriseletting.web.manage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.sunriseletting.web.security.SecurityConfiguration;
import com.sunriseletting.web.testsupport.Html;
import com.sunriseletting.web.testsupport.SignedInUsers;
import jakarta.servlet.http.Cookie;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(ManageController.class)
@Import(SecurityConfiguration.class)
class ManageControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void homeHasTheTitleAndOnlyTheHeadingInMain() {
        Document page = managerHome();

        assertThat(page.title()).as("the agreed title").isEqualTo("Property Manager area - Sunrise Letting");
        assertThat(page.select("main > h1").text()).as("the agreed heading").isEqualTo("Property Manager area");
        assertThat(page.select("main > *")).as("main holds only the heading until #8").hasSize(1);
        assertThat(page.select(".notification-banner")).as("no banner on an ordinary visit").isEmpty();
    }

    @Test
    void homeHasTheBandInsideTheHeaderAfterTheNav() {
        Document page = managerHome();

        Element band = page.selectFirst("header.site-header > .site-header__inner:has(nav) + div.area-band");
        assertThat(band).as("the band should sit inside the header, straight after the nav").isNotNull();
        assertThat(band.select("> p.area-band__text").text()).as("the band's text").isEqualTo("Property Manager area");
        assertThat(band.select("a, h1, h2, h3")).as("the band is a label, not a link or a heading").isEmpty();
        assertThat(page.select(".area-band")).as("there is one band").hasSize(1);
    }

    @Test
    void homeHeaderHasTheThreeItemsWithTheAreaAsCurrent() {
        Document page = managerHome();

        assertThat(page.select("header nav li").eachText()).as("the Property Manager's items, in order")
                .containsExactly("Properties to let", "Property Manager area", "Sign out");
        assertThat(page.select("header nav li a").eachAttr("href")).as("where the links go").containsExactly("/properties", "/manage");
        assertThat(page.select("header nav [aria-current=page]").eachText()).as("only the area should be current").containsExactly("Property Manager area");
        Element form = page.selectFirst("header nav li:last-child form.sign-out[method=post][action=/sign-out]");
        assertThat(form).as("sign out is the last item").isNotNull();
        assertThat(form.select("input[type=hidden][name=_csrf]").attr("value")).as("the form carries a CSRF token").isNotBlank();
        assertThat(page.select("a[href=/sign-in], a[href=/create-account]")).as("those links aren't rendered at all").isEmpty();
    }

    @Test
    void homeHasTheTimeOutDialogWithTheSessionsTwoHours() {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(7200);

        MvcTestResult result = mvc.get().uri("/manage").session(session).with(authentication(SignedInUsers.propertyManager(false))).exchange();

        assertThat(result).as("the page should load").hasStatusOk();
        Document page = Html.page(result);
        assertThat(page.select("#timeout-dialog").attr("data-timeout-seconds")).as("two hours").isEqualTo("7200");
        assertThat(page.select("#timeout-dialog").attr("data-warning-seconds")).as("two minutes before the end").isEqualTo("120");
        assertThat(page.select("head script[src=/js/session-timeout.js]")).as("the script is on the page").hasSize(1);
    }

    @Test
    void signedOutGoesToSignInWithNoMessage() {
        MvcTestResult result = mvc.get().uri("/manage").exchange();

        assertThat(result).as("the browser should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("no timedOut for someone never signed in").hasRedirectedUrl("/sign-in?returnTo=%2Fmanage");
    }

    @Test
    void anExpiredSessionGoesToTheTimedOutAddress() {
        MvcTestResult result = mvc.get().uri("/manage").cookie(new Cookie("signed-in", "1")).exchange();

        assertThat(result).as("the browser should be redirected").hasStatus(HttpStatus.FOUND);
        assertThat(result).as("with the message and the return address").hasRedirectedUrl("/sign-in?timedOut=true&returnTo=%2Fmanage");
    }

    @Test
    void aVisitorGetsTheAccessDeniedPage() {
        MvcTestResult result = mvc.get().uri("/manage").with(authentication(SignedInUsers.visitor())).exchange();

        assertThat(result).as("a Visitor is denied now the page exists").hasStatus(HttpStatus.FORBIDDEN);
        Document page = Html.page(result);
        assertThat(page.select("main h1").text()).as("the access-denied page").isEqualTo("You do not have permission to view this page");
        assertThat(page.select(".area-band")).as("no band for a Visitor").isEmpty();
        assertThat(page.select("header nav li").eachText()).as("the Visitor's items").containsExactly("Properties to let", "Sign out");
    }

    @Test
    void theStylesheetStylesTheBand() {
        MvcTestResult result = mvc.get().uri("/css/site.css").exchange();

        assertThat(result).bodyText().as("the stylesheet should style the band").contains(".area-band").contains(".area-band__text");
    }

    private Document managerHome() {
        MvcTestResult result = mvc.get().uri("/manage").with(authentication(SignedInUsers.propertyManager(false))).exchange();
        assertThat(result).as("the page should load").hasStatusOk();
        return Html.page(result);
    }
}
