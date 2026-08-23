package org.telegram.margelet;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Копирование сообщения вместе с оформлением.
 *
 * Обычное копирование в телеграме кладёт в буфер голый текст: жирный, курсив и
 * ссылки теряются по дороге. Здесь в буфер кладётся два представления сразу —
 * обычный текст и то же самое разметкой HTML. Кто вставит в блокнот, получит
 * текст; кто вставит туда, где разметку понимают, получит оформление.
 *
 * Своё оформление форка остаётся в тексте невидимыми метками: вставишь такое
 * обратно в Margelet — оно снова заиграет.
 */
public class MargeletCopy {

    /** Открывающая или закрывающая метка HTML для этого вида разметки. */
    private static String tag(TLRPC.MessageEntity entity, boolean open) {
        final String name;
        if (entity instanceof TLRPC.TL_messageEntityBold) {
            name = "b";
        } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
            name = "i";
        } else if (entity instanceof TLRPC.TL_messageEntityUnderline) {
            name = "u";
        } else if (entity instanceof TLRPC.TL_messageEntityStrike) {
            name = "s";
        } else if (entity instanceof TLRPC.TL_messageEntityCode
                || entity instanceof TLRPC.TL_messageEntityPre) {
            name = "code";
        } else if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
            name = "blockquote";
        } else if (entity instanceof TLRPC.TL_messageEntitySpoiler) {
            // Пары для спойлера в HTML нет. Ближе всего текст цветом фона: он
            // так же не читается, пока не выделишь.
            return open ? "<span style=\"color:transparent;background:#555\">" : "</span>";
        } else if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
            return open ? "<a href=\"" + escape(((TLRPC.TL_messageEntityTextUrl) entity).url) + "\">" : "</a>";
        } else {
            return null;
        }
        return open ? "<" + name + ">" : "</" + name + ">";
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Собирает HTML по разметке сообщения.
     *
     * Идём по тексту один раз и на каждой границе выписываем метки: сначала
     * закрывающие, потом открывающие. Порядок внутри границы важен так же, как
     * в своём формате: среди открывающих первым идёт длинный кусок, среди
     * закрывающих — внутренний, иначе вложенность собирается наизнанку.
     */
    public static String html(CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
        if (text == null) {
            return null;
        }
        final List<TLRPC.MessageEntity> usable = new ArrayList<>();
        if (entities != null) {
            for (TLRPC.MessageEntity entity : entities) {
                if (tag(entity, true) != null && entity.offset >= 0 && entity.length > 0
                        && entity.offset + entity.length <= text.length()) {
                    usable.add(entity);
                }
            }
        }
        final List<TLRPC.MessageEntity> opening = new ArrayList<>(usable);
        // Длинный кусок открывается раньше короткого.
        Collections.sort(opening, (a, b) -> a.offset != b.offset
                ? Integer.compare(a.offset, b.offset)
                : Integer.compare(b.length, a.length));
        final List<TLRPC.MessageEntity> closing = new ArrayList<>(usable);
        // Внутренний кусок закрывается раньше внешнего.
        Collections.sort(closing, (a, b) -> {
            final int ae = a.offset + a.length, be = b.offset + b.length;
            return ae != be ? Integer.compare(ae, be) : Integer.compare(b.offset, a.offset);
        });

        final StringBuilder out = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            for (TLRPC.MessageEntity entity : closing) {
                if (entity.offset + entity.length == i) {
                    out.append(tag(entity, false));
                }
            }
            for (TLRPC.MessageEntity entity : opening) {
                if (entity.offset == i) {
                    out.append(tag(entity, true));
                }
            }
            if (i == text.length()) {
                break;
            }
            final char c = text.charAt(i);
            if (c == '&') {
                out.append("&amp;");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == '\n') {
                out.append("<br>");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Кладёт сообщение в буфер вместе с оформлением. */
    public static void copy(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return;
        }
        final CharSequence plain = message.messageOwner.message;
        if (TextUtils.isEmpty(plain)) {
            return;
        }
        final String asHtml = html(plain, message.messageOwner.entities);
        if (TextUtils.isEmpty(asHtml)) {
            AndroidUtilities.addToClipboard(plain);
        } else {
            // Через двухдоводный addToClipboard: он не чистит текст, а
            // одинарный нарочно вычищает наши метки — здесь они и есть смысл.
            AndroidUtilities.addToClipboard(plain, asHtml);
        }
    }
}
