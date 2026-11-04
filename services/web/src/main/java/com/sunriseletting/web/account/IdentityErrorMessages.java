package com.sunriseletting.web.account;

import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** Turns identity's error codes into the page copy in messages.properties. */
@Component
public class IdentityErrorMessages {

    private static final Logger log = LoggerFactory.getLogger(IdentityErrorMessages.class);

    private final MessageSource messages;

    IdentityErrorMessages(MessageSource messages) {
        this.messages = messages;
    }

    /** Empty for a code this service has no copy for, which is a response outside the contract. */
    public Optional<String> messageFor(String code, Locale locale) {
        Optional<String> message = Optional.ofNullable(messages.getMessage("identity.error." + code, null, null, locale));
        if (message.isEmpty()) {
            log.warn("Identity answered with an error code that has no copy: {}", code);
        }
        return message;
    }
}
