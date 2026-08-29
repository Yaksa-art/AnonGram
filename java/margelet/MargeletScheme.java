package org.telegram.margelet;

import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MargeletBadgeGalleryActivity;
import org.telegram.ui.MargeletConveniencesActivity;
import org.telegram.ui.MargeletDonateActivity;
import org.telegram.ui.MargeletFontsActivity;
import org.telegram.ui.MargeletGiftsActivity;
import org.telegram.ui.MargeletInputActivity;
import org.telegram.ui.MargeletMarkdownActivity;
import org.telegram.ui.MargeletMarkupActivity;
import org.telegram.ui.MargeletPluginsActivity;
import org.telegram.ui.MargeletProfileActivity;
import org.telegram.ui.MargeletProfilesActivity;
import org.telegram.ui.MargeletSettingsActivity;
import org.telegram.ui.MargeletSoundActivity;
import org.telegram.ui.MargeletStreamerActivity;
import org.telegram.ui.MargeletUpdatesActivity;
import org.telegram.ui.MargeletWallActivity;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Свои адреса: {@code margy://донат} открывает нужную вкладку форка.
 *
 * Раньше на вкладку нельзя было сослаться никак. Объяснить человеку, где
 * лежит нужный переключатель, можно было только словами — «настройки, потом
 * Margy, потом разметка, потом вот это». Теперь на неё можно дать ссылку, и
 * она откроется нажатием, как любая другая.
 *
 * Работает это в две руки, и обе нужны.
 *
 * Первая — <b>показать</b>. Телеграм сам такую схему не знает и нажимаемой её
 * не сделает: разметку ссылок расставляет сервер, а сервер про «margy» не
 * слышал. Поэтому метку дописываем сами, при показе текста, тем же способом,
 * каким это уже делают кнопки нашей разметки, — обычной «ссылкой с текстом».
 * Дальше её рисует и ловит телеграм, своим кодом.
 *
 * Вторая — <b>открыть</b>. Нажатая ссылка приходит в
 * {@link Browser#openUrl} или в окно «открыть ссылку?», и в обоих местах мы
 * забираем свои адреса себе. Не забрать — значит отдать их андроиду, который
 * скажет, что открывать «margy://» некому.
 *
 * Что не вкладка, а место снаружи — канал, форум, исходники — уводим в
 * обычную ссылку. Незнакомое имя не проглатываем молча: человек написал
 * адрес и ждёт хоть какого-то ответа, а тихое ничего он примет за поломку.
 */
public class MargeletScheme {

    public static final String SCHEME = "margy";
    private static final String PREFIX = SCHEME + ":";

    /**
     * Как ссылка выглядит в тексте.
     *
     * Хвост нарочно узкий: буквы, цифры и немного знаков. Пробел, запятая и
     * скобка в адрес не попадут, а значит «открой margy://donate, там всё»
     * не утащит запятую внутрь ссылки.
     */
    private static final Pattern LINK =
            Pattern.compile("(?i)margy://[A-Za-z0-9_@./-]*");

    /** Вкладка «Магазин» в плагинах — вторая по счёту. */
    private static final int TAB_STORE = 1;

    /** Наш ли это адрес. Пробелы по краям срезаем — иначе своё же и не узнаем. */
    public static boolean is(String url) {
        if (url == null) {
            return false;
        }
        final String value = url.trim();
        return value.length() >= PREFIX.length()
                && value.substring(0, PREFIX.length()).equalsIgnoreCase(PREFIX);
    }

    public static boolean is(Uri uri) {
        return uri != null && SCHEME.equalsIgnoreCase(uri.getScheme());
    }

    /**
     * Имя вкладки: то, что идёт сразу за схемой.
     *
     * Разбираем строкой, а не через {@link Uri}: «margy://donate» и
     * «margy:donate» для него разные вещи — первое с хозяином, второе без, — а
     * для человека это один и тот же адрес.
     */
    public static String nameOf(String url) {
        final String rest = tailOf(url);
        if (rest == null) {
            return null;
        }
        return cut(rest, 0).toLowerCase(Locale.ROOT);
    }

    /** Что человек дописал после имени: «margy://wall/12345» — «12345». */
    public static String argOf(String url) {
        final String rest = tailOf(url);
        if (rest == null) {
            return "";
        }
        final int slash = rest.indexOf('/');
        if (slash < 0) {
            return "";
        }
        return cut(rest.substring(slash + 1), 0);
    }

    /** Всё после схемы и ведущих косых. Null — адрес не наш. */
    private static String tailOf(String url) {
        if (!is(url)) {
            return null;
        }
        String value = url.trim().substring(PREFIX.length());
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    /** Кусок до первой косой, вопроса или решётки. */
    private static String cut(String value, int from) {
        for (int i = from; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                return value.substring(from, i);
            }
        }
        return value.substring(from);
    }

    /**
     * Открыть вкладку по адресу.
     *
     * @return взяли ли мы этот адрес себе. Ложь — адрес чужой, пусть идёт
     *         дальше обычной дорогой
     */
    public static boolean open(String url) {
        if (!is(url)) {
            return false;
        }
        final String name = nameOf(url);
        final String arg = argOf(url);
        // На главный поток: отсюда открываются экраны, а их заводит только он.
        AndroidUtilities.runOnUIThread(() -> show(name, arg));
        return true;
    }

    public static boolean open(Uri uri) {
        return is(uri) && open(uri.toString());
    }

    private static void show(String name, String arg) {
        final BaseFragment from = LaunchActivity.getLastFragment();
        if (from == null) {
            // Показывать некуда: приложение свёрнуто или ещё не поднялось.
            return;
        }
        try {
            final String outside = outsideOf(name);
            if (outside != null) {
                Browser.openUrl(from.getContext(), outside);
                return;
            }
            if ("wall".equals(name)) {
                wall(from, arg);
                return;
            }
            final BaseFragment screen = screenOf(name);
            if (screen != null) {
                from.presentFragment(screen);
                return;
            }
            BulletinFactory.of(from).createSimpleBulletin(R.raw.error,
                    LocaleController.formatString(R.string.MargeletSchemeUnknown, name)).show();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /**
     * Вкладка по имени. Null — такой нет.
     *
     * Имена те же, что человек видит в разделе: «донат» лежит под
     * «margy://donate», а не под номером строки. Номер строки поменяется на
     * следующей же неделе, имя — нет.
     */
    private static BaseFragment screenOf(String name) {
        switch (name) {
            case "":
            case "settings":
            case "margy":
                return new MargeletSettingsActivity();
            case "donate":
                return new MargeletDonateActivity();
            case "plugins":
                return new MargeletPluginsActivity();
            case "store":
                return new MargeletPluginsActivity(TAB_STORE);
            case "markup":
                return new MargeletMarkupActivity();
            case "markdown":
                return new MargeletMarkdownActivity();
            case "input":
                return new MargeletInputActivity();
            case "sound":
                return new MargeletSoundActivity();
            case "streamer":
                return new MargeletStreamerActivity();
            case "conveniences":
                return new MargeletConveniencesActivity();
            case "profiles":
                return new MargeletProfilesActivity();
            case "profile":
                return new MargeletProfileActivity();
            case "badges":
                return new MargeletBadgeGalleryActivity();
            case "fonts":
                return new MargeletFontsActivity();
            case "gifts":
                return new MargeletGiftsActivity();
            case "updates":
                return new MargeletUpdatesActivity();
        }
        return null;
    }

    /** Что открывается не вкладкой, а обычной ссылкой наружу. */
    private static String outsideOf(String name) {
        switch (name) {
            case "channel":
                return MargeletConfig.CHANNEL_URL;
            case "forum":
                return MargeletConfig.FORUM_URL;
            case "source":
                return MargeletConfig.SOURCE_URL;
            case "feedback":
                return MargeletConfig.FEEDBACK_URL;
            case "stickers":
                return MargeletConfig.STICKERS_URL;
            case "group":
                return "https://t.me/" + MargeletGroup.USERNAME;
        }
        return null;
    }

    /**
     * Стена: своя, если ничего не дописано, иначе чужая.
     *
     * Дописать можно номер («margy://wall/12345») или ник
     * («margy://wall/@narezany»). Ник сперва надо превратить в номер, а это
     * поездка на сервер, поэтому открытие ждёт ответа.
     */
    private static void wall(BaseFragment from, String arg) {
        final int account = UserConfig.selectedAccount;
        if (arg == null || arg.isEmpty()) {
            MargeletWallActivity.open(from, UserConfig.getInstance(account).getClientUserId(),
                    LocaleController.getString(R.string.MargeletWallMine));
            return;
        }
        final long id = numberOf(arg);
        if (id != 0) {
            MargeletWallActivity.open(from, id, nameOf(account, id));
            return;
        }
        final String username = arg.startsWith("@") ? arg.substring(1) : arg;
        MessagesController.getInstance(account).getUserNameResolver().resolve(username, found -> {
            if (found == null || found == 0) {
                BulletinFactory.of(from).createSimpleBulletin(R.raw.error,
                        LocaleController.formatString(R.string.MargeletSchemeUnknown, username)).show();
                return;
            }
            MargeletWallActivity.open(from, found, nameOf(account, found));
        });
    }

    /** Номер или ноль. Ник числом не является, и это здесь единственная проверка. */
    private static long numberOf(String value) {
        try {
            return Long.parseLong(value);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Как зовут владельца стены. Пусто — мы его ещё не знаем, и это не беда. */
    private static String nameOf(int account, long peerId) {
        try {
            final MessagesController controller = MessagesController.getInstance(account);
            if (peerId >= 0) {
                final TLRPC.User user = controller.getUser(peerId);
                return user == null ? "" : ContactsController.formatName(user.first_name, user.last_name);
            }
            final TLRPC.Chat chat = controller.getChat(-peerId);
            return chat == null ? "" : chat.title;
        } catch (Throwable t) {
            FileLog.e(t);
            return "";
        }
    }

    /** Есть ли в тексте хоть один наш адрес. Дешёвая проверка перед разбором. */
    public static boolean has(CharSequence text) {
        if (text == null || text.length() < PREFIX.length()) {
            return false;
        }
        final String value = text.toString();
        for (int i = 0; i + PREFIX.length() <= value.length(); i++) {
            if (value.regionMatches(true, i, PREFIX, 0, PREFIX.length())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Помечает наши адреса в тексте как ссылки.
     *
     * Кладём «ссылку с текстом», а не «просто ссылку»: вторую телеграм
     * пропускает, когда разбирает сообщение по-старому, вручную, — а по-старому
     * он разбирает как раз своё только что отправленное. Первую он чтит всегда.
     *
     * Список разметки сюда приходит уже своей копией — дописанное не должно
     * доехать до хранилища. Отсчёты здесь считаны по показываемому тексту, а в
     * хранилище лежит исходный, и попади они туда, разметка съехала бы на
     * соседние слова.
     */
    public static void injectEntities(CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
        if (text == null || entities == null || !has(text)) {
            return;
        }
        final Matcher at = LINK.matcher(text);
        while (at.find()) {
            final String url = at.group();
            if (nameOf(url) == null) {
                continue;
            }
            final TLRPC.TL_messageEntityTextUrl link = new TLRPC.TL_messageEntityTextUrl();
            link.offset = at.start();
            link.length = at.end() - at.start();
            link.url = url;
            entities.add(link);
        }
    }
}
