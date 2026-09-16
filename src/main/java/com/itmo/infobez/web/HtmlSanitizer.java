package com.itmo.infobez.web;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Защита от XSS (OWASP A03). Двухуровневая:
 * 1) на входе вся разметка вырезается санитайзером OWASP — в БД не попадает ни одного тега;
 * 2) на выходе спецсимволы экранируются, чтобы данные, попавшие в базу иным путём,
 *    не могли исполниться у клиента, который вставит их в DOM.
 */
@Component
public class HtmlSanitizer {

    /** Политика без единого разрешённого тега: любая разметка удаляется. */
    private static final PolicyFactory PLAIN_TEXT = new HtmlPolicyBuilder().toFactory();

    /** Очистка данных, поступивших от пользователя. */
    public String sanitizeInput(String value) {
        if (value == null) {
            return null;
        }
        // Санитайзер возвращает HTML-сущности (&amp;, &lt;), разворачиваем их обратно в текст.
        return HtmlUtils.htmlUnescape(PLAIN_TEXT.sanitize(value)).trim();
    }

    /** Экранирование данных, отдаваемых наружу. */
    public String escapeOutput(String value) {
        return value == null ? null : HtmlUtils.htmlEscape(value);
    }
}
