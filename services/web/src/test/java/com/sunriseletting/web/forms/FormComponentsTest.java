package com.sunriseletting.web.forms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.sunriseletting.web.security.SecurityConfiguration;
import com.sunriseletting.web.testsupport.Html;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Renders the shared fragments in fragments/form and fragments/notification-banner through a test-only page. */
@WebMvcTest(FormComponentsTest.FixtureController.class)
@Import({ SecurityConfiguration.class, FormComponentsTest.FixtureController.class })
class FormComponentsTest {

    @Controller
    static class FixtureController {

        @GetMapping("/fixtures/form-components")
        String page(Model model) {
            model.addAttribute("summaryErrors", List.of(
                    new SummaryError("first-name", "Enter your first name"),
                    new SummaryError("email", "Enter your email address")));
            return "fixtures/form-components";
        }
    }

    @Autowired
    private MockMvcTester mvc;

    private Document page;

    @BeforeEach
    void renderFixture() {
        MvcTestResult result = mvc.get().uri("/fixtures/form-components").exchange();
        assertThat(result).as("fixture page should render").hasStatusOk();
        page = Html.page(result);
    }

    @Test
    void neutralFieldHasGroupLabelAndControlAndNoErrorState() {
        Element group = page.selectFirst("#field-neutral");

        assertThat(group.hasClass("form-group")).as("the group should be a form group").isTrue();
        assertThat(group.hasClass("form-group--error")).as("a neutral group should have no error class").isFalse();
        assertThat(group.select("label.label[for=neutral]").text()).as("the label should name the field").isEqualTo("Neutral");
        Element input = group.selectFirst("input.input");
        assertThat(input.id()).as("the input id should be set").isEqualTo("neutral");
        assertThat(input.attr("name")).as("the input name should be set").isEqualTo("neutralName");
        assertThat(input.attr("type")).as("the input type should be set").isEqualTo("text");
        assertThat(input.attr("autocomplete")).as("autocomplete should be passed through").isEqualTo("given-name");
        assertThat(input.attr("spellcheck")).as("spellcheck should be passed through").isEqualTo("false");
        assertThat(input.hasAttr("aria-invalid")).as("a neutral input should have no aria-invalid").isFalse();
        assertThat(input.hasAttr("aria-describedby")).as("no hint or error means nothing to describe it").isFalse();
        assertThat(group.select(".error-message")).as("a neutral group should have no error message").isEmpty();
        assertThat(input.hasAttr("hx-post")).as("hx- attributes are off unless asked for").isFalse();
        assertThat(input.hasAttr("hx-trigger")).as("hx- attributes are off unless asked for").isFalse();
    }

    @Test
    void fieldRendersItsValueExactlyAsGiven() {
        assertThat(page.selectFirst("#neutral").attr("value")).as("the value shouldn't be trimmed or changed")
                .isEqualTo("  Typed As Is ");
        assertThat(page.selectFirst("#wrong").attr("value")).as("the value should be kept and safely escaped")
                .isEqualTo("<bad>");
    }

    @Test
    void hintComesAfterTheLabelAndDescribesTheInput() {
        Element group = page.selectFirst("#field-hinted");

        assertThat(group.select("p.hint#hinted-hint").text()).as("the hint should be shown").isEqualTo("A hint");
        assertThat(group.child(1).id()).as("the hint should follow the label").isEqualTo("hinted-hint");
        assertThat(group.selectFirst("input").attr("aria-describedby")).as("the input should reference the hint")
                .isEqualTo("hinted-hint");
    }

    @Test
    void errorStateHasClassMessagePrefixAndAriaAttributes() {
        Element group = page.selectFirst("#field-wrong");

        assertThat(group.hasClass("form-group--error")).as("the group should show the error state").isTrue();
        Element message = group.selectFirst("p.error-message#wrong-error");
        assertThat(message.text()).as("the message should have the hidden prefix").isEqualTo("Error: Enter it properly");
        assertThat(message.select("span.visually-hidden").text()).as("the prefix should be visually hidden").isEqualTo("Error:");
        Element input = group.selectFirst("input");
        assertThat(input.attr("aria-invalid")).as("the input should be marked invalid").isEqualTo("true");
        assertThat(input.attr("aria-describedby")).as("the input should reference the hint and the error")
                .isEqualTo("wrong-hint wrong-error");
        assertThat(group.children().eachAttr("id")).as("order should be label, hint, error, control")
                .containsExactly("wrong-hint", "wrong-error", "wrong");
    }

    @Test
    void hxAttributesAreRenderedWhenAskedFor() {
        Element input = page.selectFirst("#live");

        assertThat(input.attr("hx-post")).as("hx-post should be passed through").isEqualTo("/validate/live");
        assertThat(input.attr("hx-trigger")).as("the check runs on blur").isEqualTo("blur");
        assertThat(input.attr("hx-target")).as("the whole group is swapped").isEqualTo("#field-live");
        assertThat(input.attr("hx-swap")).as("the whole group is swapped").isEqualTo("outerHTML");
        assertThat(input.attr("hx-params")).as("the allow-list should be passed through").isEqualTo("liveName,_csrf");
    }

    @Test
    void controlsHaveNoRequiredOrMaxlength() {
        assertThat(page.select("[required], [maxlength]")).as("the server reports these errors itself (6.6)").isEmpty();
    }

    @Test
    void selectListsThePlaceholderThenOptionsAndSelectsTheValue() {
        Element group = page.selectFirst("#field-choice");
        Element select = group.selectFirst("select.input#choice");

        assertThat(select.attr("name")).as("the select name should be set").isEqualTo("choice");
        assertThat(select.attr("autocomplete")).as("autocomplete should be passed through").isEqualTo("honorific-prefix");
        assertThat(select.select("option").eachText()).as("options should follow the placeholder").containsExactly("Pick one", "A", "B");
        assertThat(select.select("option").eachAttr("value")).as("the placeholder has an empty value").containsExactly("", "A", "B");
        assertThat(select.select("option[selected]").eachText()).as("the given value should be selected").containsExactly("B");
    }

    @Test
    void selectHasTheSameErrorStateAndHxAttributes() {
        Element group = page.selectFirst("#field-choice-wrong");
        Element select = group.selectFirst("select");

        assertThat(group.hasClass("form-group--error")).as("the group should show the error state").isTrue();
        assertThat(select.attr("aria-invalid")).as("the select should be marked invalid").isEqualTo("true");
        assertThat(select.attr("aria-describedby")).as("the select should reference the error").isEqualTo("choice-wrong-error");
        assertThat(select.select("option[selected]")).as("nothing is chosen for the user").isEmpty();
        assertThat(select.attr("hx-post")).as("hx-post should be passed through").isEqualTo("/validate/choice");
        assertThat(select.attr("hx-target")).as("the whole group is swapped").isEqualTo("#field-choice-wrong");
    }

    @Test
    void passwordFieldHasTheComponentHooks() {
        Element group = page.selectFirst("#field-secret");

        assertThat(group.hasClass("form-group")).as("it should be a form group").isTrue();
        assertThat(group.hasClass("password-field")).as("it should be a password field").isTrue();
        assertThat(group.hasClass("form-group--error")).as("no error state without an error").isFalse();
        assertThat(group.select("label.label[for=secret]").text()).as("the label should name the field").isEqualTo("Secret");
        assertThat(group.select("p.hint#secret-hint").text()).as("the hint should be shown").isEqualTo("A secret hint");
        assertThat(group.select(".password-field__error-slot#secret-error-slot")).as("the slot should always be present").hasSize(1);
        assertThat(group.select("#secret-error-slot").first().children()).as("the slot is empty without an error").isEmpty();

        Element input = group.selectFirst(".password-field__wrapper input.input.password-field__input#secret");
        assertThat(input.attr("type")).as("the input should be masked").isEqualTo("password");
        assertThat(input.attr("name")).as("the input name should be set").isEqualTo("secret");
        assertThat(input.hasAttr("value")).as("a password input never has a value").isFalse();
        assertThat(input.attr("autocomplete")).as("autocomplete should be passed in").isEqualTo("new-password");
        assertThat(input.attr("spellcheck")).as("no spellcheck on passwords").isEqualTo("false");
        assertThat(input.attr("autocapitalize")).as("no capitalising on passwords").isEqualTo("none");
        assertThat(input.attr("aria-describedby")).as("the input should always list the hint and the error id")
                .isEqualTo("secret-hint secret-error");
        assertThat(input.hasAttr("aria-invalid")).as("no aria-invalid without an error").isFalse();

        Element toggle = group.selectFirst(".password-field__wrapper button.button.button--secondary.password-field__toggle");
        assertThat(toggle.attr("type")).as("the toggle must not submit the form").isEqualTo("button");
        assertThat(toggle.hasAttr("hidden")).as("the toggle is hidden until the script reveals it").isTrue();
        assertThat(toggle.attr("aria-controls")).as("the toggle should control the input").isEqualTo("secret");
        assertThat(toggle.text()).as("the toggle should have a unique accessible name").isEqualTo("Show secret");
        assertThat(toggle.select("span.visually-hidden").text()).as("the suffix should be visually hidden").isEqualTo("secret");

        Element status = group.selectFirst("p.visually-hidden.password-field__status");
        assertThat(status.attr("aria-live")).as("the status should be announced politely").isEqualTo("polite");
        assertThat(status.text()).as("the status starts empty").isEmpty();
    }

    @Test
    void passwordFieldErrorStateHasMessageInTheSlot() {
        Element group = page.selectFirst("#field-secret-wrong");

        assertThat(group.hasClass("form-group--error")).as("the group should show the error state").isTrue();
        assertThat(group.select("#secret-wrong-error-slot > p.error-message#secret-wrong-error").text())
                .as("the message should be in the slot, with the hidden prefix").isEqualTo("Error: Bad secret");
        Element input = group.selectFirst("input");
        assertThat(input.attr("aria-invalid")).as("the input should be marked invalid").isEqualTo("true");
        assertThat(input.attr("aria-describedby")).as("the input should list the error id").isEqualTo("secret-wrong-error");
        assertThat(input.attr("autocomplete")).as("autocomplete should be passed in").isEqualTo("current-password");
        assertThat(input.hasAttr("value")).as("a password input never has a value").isFalse();
    }

    @Test
    void passwordFieldHxAttributesTargetOnlyTheErrorSlot() {
        Element input = page.selectFirst("#secret-wrong");

        assertThat(input.attr("hx-post")).as("hx-post should be passed through").isEqualTo("/validate/secret");
        assertThat(input.attr("hx-trigger")).as("the check runs on blur").isEqualTo("blur");
        assertThat(input.attr("hx-target")).as("only the error slot is swapped").isEqualTo("#secret-wrong-error-slot");
        assertThat(input.attr("hx-swap")).as("only the slot's content is swapped").isEqualTo("innerHTML");
        assertThat(input.attr("hx-params")).as("the allow-list should be passed through").isEqualTo("secret,secretWrong,_csrf");
        assertThat(page.selectFirst("#secret").hasAttr("hx-post")).as("hx- attributes are off unless asked for").isFalse();
    }

    @Test
    void errorSummaryListLinksToTheInputs() {
        Element summary = page.select(".error-summary").get(0);

        assertThat(summary.attr("tabindex")).as("the summary should take focus").isEqualTo("-1");
        assertThat(summary.hasAttr("autofocus")).as("the summary should take focus").isTrue();
        assertThat(summary.attr("aria-labelledby")).as("the heading should name the summary").isEqualTo("error-summary-title");
        assertThat(summary.select("h2#error-summary-title").text()).as("the heading text is fixed").isEqualTo("There is a problem");
        assertThat(summary.select("ul li a").eachText()).as("links should use the field messages")
                .containsExactly("Enter your first name", "Enter your email address");
        assertThat(summary.select("ul li a").eachAttr("href")).as("links should go to the input ids")
                .containsExactly("#first-name", "#email");
    }

    @Test
    void errorSummaryFormLevelVariantHasAParagraphNotAList() {
        Element summary = page.select(".error-summary").get(1);

        assertThat(summary.hasAttr("autofocus")).as("the summary should take focus").isTrue();
        assertThat(summary.select("h2").text()).as("the heading text is fixed").isEqualTo("There is a problem");
        assertThat(summary.select("ul")).as("the form-level variant has no list").isEmpty();
        assertThat(summary.select("p.error-summary__body").text()).as("the message should be shown").isEqualTo("Plain message.");
        assertThat(summary.select("p.error-summary__body a")).as("no link unless asked for").isEmpty();
    }

    @Test
    void errorSummaryFormLevelVariantMayContainALink() {
        Element summary = page.select(".error-summary").get(2);

        assertThat(summary.select("p.error-summary__body").text()).as("the text should read as one sentence")
                .isEqualTo("Before the link, follow it.");
        assertThat(summary.select("p.error-summary__body a").attr("href")).as("the link should go where it was told").isEqualTo("/somewhere");
    }

    @Test
    void successBannerHasTheComponentHooks() {
        Element banner = page.selectFirst(".notification-banner.notification-banner--success");

        assertThat(banner.attr("role")).as("the banner should be announced").isEqualTo("status");
        assertThat(banner.attr("tabindex")).as("the banner should take focus").isEqualTo("-1");
        assertThat(banner.hasAttr("autofocus")).as("the banner should take focus").isTrue();
        assertThat(banner.select("h2").text()).as("the banner should have a heading").isEqualTo("It worked");
    }

    @Test
    void infoBannerHasTheComponentHooksAndCanTakeFocus() {
        Element banner = page.selectFirst("#info-focused .notification-banner.notification-banner--info");

        assertThat(banner.attr("role")).as("the banner should be announced").isEqualTo("status");
        assertThat(banner.attr("tabindex")).as("the banner can take focus").isEqualTo("-1");
        assertThat(banner.hasAttr("autofocus")).as("the banner should take focus").isTrue();
        assertThat(banner.select("h2").text()).as("the banner should have a heading").isEqualTo("Just so you know");
    }

    @Test
    void infoBannerGivesUpFocusWhenAnErrorSummaryHasIt() {
        Element banner = page.selectFirst("#info-unfocused .notification-banner--info");

        assertThat(banner.attr("role")).as("the banner is still announced").isEqualTo("status");
        assertThat(banner.hasAttr("autofocus")).as("the banner shouldn't take focus").isFalse();
    }

    @Test
    void checkboxWithoutAHintIsUntickedAndDescribedByNothing() {
        Element input = page.selectFirst(".form-group.checkbox input#plain-box.checkbox__input[type=checkbox]");

        assertThat(input.attr("name")).as("the name should be passed in").isEqualTo("plainBox");
        assertThat(input.attr("value")).as("a ticked box posts on").isEqualTo("on");
        assertThat(input.hasAttr("checked")).as("it should be unticked").isFalse();
        assertThat(input.hasAttr("aria-describedby")).as("no hint means nothing to describe it").isFalse();
        assertThat(page.select("label.checkbox__label[for=plain-box]").text()).as("the label should be clickable").isEqualTo("Plain box");
        assertThat(page.select("#plain-box-hint")).as("no hint unless given").isEmpty();
    }

    @Test
    void checkboxWithAHintHasItAfterTheLabelAndDescribingTheInput() {
        Element input = page.selectFirst("input#hinted-box");

        assertThat(input.hasAttr("checked")).as("it should be ticked").isTrue();
        assertThat(input.attr("aria-describedby")).as("the hint describes it").isEqualTo("hinted-box-hint");
        Element label = page.selectFirst("label[for=hinted-box]");
        assertThat(label.nextElementSibling().is("p.hint.checkbox__hint#hinted-box-hint")).as("the hint follows the label").isTrue();
        assertThat(label.nextElementSibling().text()).as("the hint's text").isEqualTo("A box hint");
    }

    @Test
    void stylesheetStylesTheComponents() {
        MvcTestResult result = mvc.get().uri("/css/site.css").exchange();

        assertThat(result).bodyText().as("the stylesheet should style the form components")
                .contains(".form-group--error").contains(".error-summary").contains(".password-field__wrapper")
                .contains(".notification-banner--success").contains(".notification-banner--info").contains(".checkbox__input");
    }
}
