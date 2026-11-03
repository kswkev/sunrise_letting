package com.sunriseletting.web.account;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** Turns identity's error codes into the page copy in messages.properties. */
@Component
public class IdentityErrorMessages {

    private final MessageSource messages;

    IdentityErrorMessages(MessageSource messages) {
        this.messages = messages;
    }

    public String messageFor(String code, Locale locale) {
        return messages.getMessage("identity.error." + code, null, locale);
    }
}
