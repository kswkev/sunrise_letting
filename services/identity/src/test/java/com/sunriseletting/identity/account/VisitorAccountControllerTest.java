package com.sunriseletting.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.sunriseletting.identity.validation.AccountDetails;
import com.sunriseletting.identity.validation.AccountField;
import com.sunriseletting.identity.validation.AccountFieldError;

@WebMvcTest(VisitorAccountController.class)
class VisitorAccountControllerTest {

    private static final AccountDetails DETAILS = new AccountDetails("Other", "Rev", "Jo", "Smith", "jo@example.com",
            "07700 900123", "Abcdefg1!", "Abcdefg1!");

    private static final String JSON = """
            {"title":"Other","titleOther":"Rev","firstName":"Jo","surname":"Smith","email":"jo@example.com",
             "phone":"07700 900123","password":"Abcdefg1!","confirmPassword":"Abcdefg1!"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private VisitorAccountService service;

    private MvcTestResult post() {
        return mvc.post().uri("/api/accounts/visitors").contentType(MediaType.APPLICATION_JSON).content(JSON).exchange();
    }

    @Test
    void createdAccountGivesItsIdAndRole() {
        UUID id = UUID.randomUUID();
        when(service.create(DETAILS)).thenReturn(new CreateVisitorAccountResult.Created(id, Role.VISITOR));

        MvcTestResult result = post();

        verify(service, description("the service should get every field of the request")).create(DETAILS);
        assertThat(result).as("a created account should give 201").hasStatus(201);
        assertThat(result).bodyJson().as("the response should give the id and role")
                .isStrictlyEqualTo("{\"accountId\":\"" + id + "\",\"role\":\"VISITOR\"}");
    }

    @Test
    void invalidDetailsGiveOneCodePerFieldUnderTheirApiNames() {
        when(service.create(DETAILS)).thenReturn(new CreateVisitorAccountResult.Invalid(Map.of(
                AccountField.TITLE_OTHER, AccountFieldError.TITLE_OTHER_REQUIRED,
                AccountField.CONFIRM_PASSWORD, AccountFieldError.PASSWORDS_DO_NOT_MATCH)));

        MvcTestResult result = post();

        verify(service, description("the service should get every field of the request")).create(DETAILS);
        assertThat(result).as("invalid details should give 400").hasStatus(400);
        assertThat(result).bodyJson().as("the response should have one code per failing field")
                .isStrictlyEqualTo("""
                        {"errors":{"titleOther":"TITLE_OTHER_REQUIRED","confirmPassword":"PASSWORDS_DO_NOT_MATCH"}}
                        """);
    }

    @Test
    void emailInUseGivesAResponseWithNoDetail() {
        when(service.create(DETAILS)).thenReturn(new CreateVisitorAccountResult.EmailInUse());

        MvcTestResult result = post();

        verify(service, description("the service should get every field of the request")).create(DETAILS);
        assertThat(result).as("an email in use should give 409").hasStatus(409);
        assertThat(result).bodyJson().as("the response should only say the account was not created")
                .isStrictlyEqualTo("{\"error\":\"ACCOUNT_NOT_CREATED\"}");
    }
}
