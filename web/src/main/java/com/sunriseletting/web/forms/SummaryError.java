package com.sunriseletting.web.forms;

/** One line of the error summary (design-system.md 4.13): the id of the input it links to, and the field's message. */
public record SummaryError(String inputId, String message) {
}
