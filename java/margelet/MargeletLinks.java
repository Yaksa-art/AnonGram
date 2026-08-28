package org.telegram.margelet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Какие ссылки пускаем в стену и в баннеры, а какие нет.
 *
 * Разрешено ровно шесть мест: телеграм, ютуб, твиттер, реддит, вконтакте,
 * ватсап. Всё остальное — сообщение не уходит и не показывается. Правило
 * грубое нарочно: стена задумана как место, где о человеке говорят другие, и
 * первое, что туда понесут, — ссылки на развод.
 *
 * Две тонкости, без которых правило обходится одним движением, и обе учтены:
 *
 * Первая. В телеграме видимый текст ссылки и её адрес — разные вещи. Написать
 * «youtube.com», а вести на что угодно, может кто угодно. Поэтому проверяется
 * не только текст, но и адрес под ним, отдельным вызовом.
 *
 * Вторая. «youtube.com.scam.ru» — это не ютуб, это scam.ru. Сравнение «конец
 * адреса совпадает» без границы по точке само становится дырой, поэтому
 * совпадение засчитывается либо целиком, либо по точке слева.
 *
 * Чего это не умеет — сказано прямо, чтобы на него не рассчитывали сверх
 * меры: «пиши мне @кто-то» пройдёт, потому что телеграм разрешён, а «зайди на
 * скам точка ру» словами не поймает ни один такой список. Это работа для
 * бота-чистильщика и для бана, а не для проверки текста.
 */
public class MargeletLinks {

    /** Разрешённые места. Сравнение идёт по концу имени, с границей по точке. */
    private static final String[] ALLOWED = {
            "t.me", "telegram.me", "telegram.org", "telegram.dog",
            "youtube.com", "youtu.be",
            "twitter.com", "x.com",
            "reddit.com", "redd.it",
            "vk.com", "vk.ru", "vk.me",
            "whatsapp.com", "wa.me",
    };

    /**
     * Похоже ли это слово на адрес.
     *
     * Схему не требуем: «scam.ru/дай-денег» — такая же ссылка, как и с
     * «https://» впереди, и телеграм её тоже сделает нажимаемой. Требовать
     * схему значило бы ловить только вежливых.
     */
    private static boolean looksLikeUrl(String word) {
        final String lower = word.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("tg://")) {
            return true;
        }
        final int dot = lower.indexOf('.');
        if (dot <= 0 || dot == lower.length() - 1) {
            return false;
        }
        // Что-то вроде «имя.зона», где зона — буквы. «3.14» и «конец.» мимо.
        final String tail = lower.substring(dot + 1);
        int letters = 0;
        while (letters < tail.length() && Character.isLetter(tail.charAt(letters))) {
            letters++;
        }
        return letters >= 2;
    }

    /** Имя хозяина адреса: без схемы, без пути, без порта и без «www.». */
    static String hostOf(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        final int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        final int at = value.indexOf('@');
        if (at >= 0) {
            // «user@host» — хозяин тот, что справа, и обманывать этим не дадим.
            value = value.substring(at + 1);
        }
        int end = value.length();
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == '/' || c == '?' || c == '#' || c == ':') {
                end = i;
                break;
            }
        }
        value = value.substring(0, end);
        if (value.startsWith("www.")) {
            value = value.substring(4);
        }
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /** Разрешено ли это место. Совпадение целиком или по точке слева. */
    public static boolean allowed(String url) {
        final String host = hostOf(url);
        if (host.isEmpty()) {
            return true;        // не адрес — не наше дело
        }
        for (String good : ALLOWED) {
            if (host.equals(good) || host.endsWith("." + good)) {
                return true;
            }
        }
        return false;
    }

    /** Все слова текста, похожие на адреса. */
    public static List<String> urlsIn(CharSequence text) {
        final List<String> found = new ArrayList<>();
        if (text == null) {
            return found;
        }
        final String value = text.toString();
        int i = 0;
        while (i < value.length()) {
            while (i < value.length() && Character.isWhitespace(value.charAt(i))) {
                i++;
            }
            final int start = i;
            while (i < value.length() && !Character.isWhitespace(value.charAt(i))) {
                i++;
            }
            if (i > start) {
                // Знаки в конце слова к адресу не относятся: «зайди на vk.com,»
                String word = value.substring(start, i);
                while (word.length() > 0 && ",.;:!?)»\"'".indexOf(word.charAt(word.length() - 1)) >= 0) {
                    word = word.substring(0, word.length() - 1);
                }
                if (looksLikeUrl(word)) {
                    found.add(word);
                }
            }
        }
        return found;
    }

    /**
     * Можно ли пускать это сообщение.
     *
     * @param text  видимый текст
     * @param links адреса, спрятанные под текстом: в телеграме это разные вещи,
     *              и проверять только видимое — значит не проверять ничего
     */
    public static boolean clean(CharSequence text, List<String> links) {
        for (String url : urlsIn(text)) {
            if (!allowed(url)) {
                return false;
            }
        }
        if (links != null) {
            for (String url : links) {
                if (url != null && !allowed(url)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Первый запрещённый адрес — чтобы сказать человеку, что именно не так. */
    public static String firstBad(CharSequence text, List<String> links) {
        for (String url : urlsIn(text)) {
            if (!allowed(url)) {
                return hostOf(url);
            }
        }
        if (links != null) {
            for (String url : links) {
                if (url != null && !allowed(url)) {
                    return hostOf(url);
                }
            }
        }
        return null;
    }
}
