package org.telegram.margelet;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

/**
 * Общая кладовая форка: публичная группа вместо своего сервера.
 *
 * Здесь живут баннеры профилей и стены. Своего сервера нет нарочно, и не
 * только из-за денег. Сервер не может проверить, что человек — это он: у него
 * нет способа спросить телеграм. Пришлось бы закреплять номер за тем, кто
 * пришёл первым, и честно писать, что чужой номер можно занять.
 *
 * В группе этой задачи нет вовсе. Сообщение уже подписано телеграмом: пришло
 * от аккаунта — значит от него. Подделать нельзя, проверять нечего. Владелец
 * это и предложил, и решение оказалось лучше моего.
 *
 * Своё сообщение человек удаляет сам, средствами телеграма, — значит «убрать
 * баннер» работает без единой строчки на нашей стороне.
 */
public class MargeletGroup {

    /** Где всё лежит. Публичная группа, читать может кто угодно. */
    public static final String USERNAME = "margy_underground";

    /** Метка баннера. Одна на всех: чей баннер — видно по автору сообщения. */
    public static final String TAG_BANNER = "#margy_banner";

    /** Метка стены. Номер — того, О КОМ пишут, а не того, кто пишет. */
    public static String tagWall(long peerId) {
        // У каналов и групп номер отрицательный, а минус в хэштег не входит —
        // телеграм оборвал бы метку на нём, и стена канала слилась бы со
        // стеной человека с тем же числом. Поэтому им отдельная буква.
        return peerId >= 0 ? "#margy_wall_" + peerId : "#margy_wall_c" + (-peerId);
    }

    private static long groupId;
    private static boolean resolving;

    /**
     * Чья стена сейчас открыта. Ноль — ничья.
     *
     * Пока стена открыта, отправка в группу дописывает её метку сама. Так
     * человек пишет в обычное поле обычной переписки, ничего не зная про
     * метки, а мы не подделываем своё поле ввода ради одной служебной строки.
     */
    private static long wallPeer;

    /**
     * Где запомнить адрес группы между запусками.
     *
     * Имя в адрес переводит сервер, и делать это при каждом холодном запуске —
     * лишняя поездка перед первым же баннером. Адрес публичной группы не
     * меняется, помнить его можно сколько угодно.
     */
    private static final String KEY_GROUP = "margy_group_id";
    /** Кто ждёт адрес группы, пока он выясняется. */
    private static final List<Peer> waiting = new ArrayList<>();

    public interface Peer {
        void onPeer(long dialogId);
    }

    public interface Messages {
        /**
         * Найденное, новое сверху.
         *
         * {@code problem} — причина неудачи или null, если всё прошло. Пустой
         * список и неудача выглядят одинаково, если про причину не спросить, а
         * это ровно та разница, из-за которой пустой экран стены нельзя было
         * отличить от сломанного.
         */
        void onMessages(List<MessageObject> messages, String problem);
    }

    /** Открыли стену этого человека или ушли с неё. */
    public static void writingTo(long peerId) {
        wallPeer = peerId;
    }

    /**
     * Дописать метку стены к отправляемому.
     *
     * Возвращает null, если отправлять нельзя, — в тексте запрещённая ссылка.
     * Отдельная проверка на отправке нужна потому, что показ чужого мы и так
     * фильтруем, но пускать своё в группу и молча прятать его на стене было
     * бы враньём обоим: и написавшему, и читающему.
     */
    public static String tagged(String text, long dialogId) {
        if (wallPeer == 0 || text == null || dialogId != groupId || groupId == 0) {
            return text;
        }
        final String tag = tagWall(wallPeer);
        if (text.contains(tag)) {
            return text;    // уже с меткой: повторяться незачем
        }
        if (MargeletLinks.firstBad(text, null) != null) {
            return null;
        }
        return tag + "\n" + text;
    }

    /**
     * Сказать, почему сообщение не ушло.
     *
     * Молча проглотить набранный текст нельзя: человек увидит пустое поле и
     * решит, что отправил. Отказ должен быть слышен ровно там, где он
     * случился.
     */
    public static void refuse() {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                android.widget.Toast.makeText(
                        org.telegram.messenger.ApplicationLoader.applicationContext,
                        org.telegram.messenger.LocaleController.getString(
                                org.telegram.messenger.R.string.MargeletWallNoLinksShort),
                        android.widget.Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    /**
     * Убрать служебные метки из текста, который увидит человек.
     *
     * Метки нужны поиску: по ним стена и собирается. Читателю они — мусор в
     * первой строке каждого отзыва, и владелец справедливо на это указал.
     * Вырезаем и лишний перевод строки следом, иначе отзыв начинался бы с
     * пустоты.
     *
     * Само сообщение при этом не меняется: в группе метка на месте, поиск её
     * находит. Прячем только показ.
     */
    public static CharSequence hideTags(CharSequence text) {
        if (text == null || text.length() == 0) {
            return text;
        }
        final String plain = text.toString();
        if (plain.indexOf(TAG_PREFIX) < 0) {
            return text;
        }
        final java.util.regex.Matcher at = TAGS.matcher(plain);
        if (!at.find()) {
            return text;
        }
        // Собираем заново, сохраняя тип: у CharSequence может быть разметка,
        // и превращать его в простую строку значит потерять её.
        final android.text.SpannableStringBuilder out =
                new android.text.SpannableStringBuilder(text);
        int shift = 0;
        do {
            int from = at.start() - shift;
            int to = at.end() - shift;
            // Съедаем перевод строки следом, чтобы не оставлять пустую первую
            // строку там, где метка стояла отдельно.
            while (to < out.length() && (out.charAt(to) == '\n' || out.charAt(to) == ' ')) {
                to++;
            }
            out.delete(from, to);
            shift += to - from;
        } while (at.find());
        return out;
    }

    private static final String TAG_PREFIX = "#margy_";
    /** Под каким именем храним вырезанные метки в клиентских данных. */
    private static final String KEY_TAGS = "margy_tags";
    private static final java.util.regex.Pattern TAGS =
            java.util.regex.Pattern.compile("#margy_(wall_c?\\d+|banner)\\b");

    /**
     * Оставить из списка только сообщения этой стены.
     *
     * Пустая метка — значит это обычная переписка, и трогать нечего: возвращаем
     * тот же список, ничего не копируя.
     */
    public static java.util.ArrayList<MessageObject> onlyWall(
            java.util.ArrayList<MessageObject> messages, String tag) {
        if (tag == null || tag.length() == 0 || messages == null || messages.isEmpty()) {
            return messages;
        }
        final java.util.ArrayList<MessageObject> out = new java.util.ArrayList<>();
        for (MessageObject message : messages) {
            if (message == null || message.messageOwner == null) {
                continue;
            }
            // Сперва смотрим в вырезанное, потом в сам текст: до вырезания
            // метка лежит в тексте, после — только здесь.
            if (contains(message.margeletTags, tag) || hasTag(message.messageOwner, tag)) {
                out.add(message);
            }
        }
        return out;
    }

    /**
     * Вырезать служебные метки из самого сообщения — вместе с разметкой.
     *
     * Вырезать только из текста было мало, и это вылезло сразу: отсчёты
     * разметки остались прежними, а текст стал короче, поэтому «хэштежность»
     * съезжала на соседние слова. Человек видел цветной кликабельный кусок,
     * который никуда не ведёт. Правим то и другое разом, здесь, до того как
     * из сообщения соберут показ.
     *
     * Само сообщение на сервере не меняется: правим свою копию в памяти.
     */
    public static String cutTags(TLRPC.Message message) {
        if (message == null) {
            return null;
        }
        if (message.message == null || message.message.indexOf(TAG_PREFIX) < 0) {
            // Метки в тексте нет — либо её и не было, либо мы уже вырезали её
            // раньше. Разобрать одно и то же сообщение могут не один раз, а
            // второй раз вырезать уже нечего: отдаём запомненное, иначе стена
            // потеряет сообщение на ровном месте.
            return message.params != null ? message.params.get(KEY_TAGS) : null;
        }
        final java.util.regex.Matcher at = TAGS.matcher(message.message);
        final java.util.List<int[]> cuts = new ArrayList<>();
        final StringBuilder found = new StringBuilder();
        while (at.find()) {
            found.append(at.group()).append(' ');
            int to = at.end();
            // Съедаем пробелы и перевод строки следом: иначе отзыв начинался
            // бы с пустой строки там, где метка стояла отдельно.
            while (to < message.message.length()
                    && (message.message.charAt(to) == '\n' || message.message.charAt(to) == ' ')) {
                to++;
            }
            cuts.add(new int[]{at.start(), to});
        }
        if (cuts.isEmpty()) {
            return null;
        }
        final StringBuilder text = new StringBuilder(message.message);
        for (int i = cuts.size() - 1; i >= 0; i--) {
            text.delete(cuts.get(i)[0], cuts.get(i)[1]);
        }
        message.message = text.toString();
        message.entities = shift(message.entities, cuts);
        // params — место для клиентских данных о сообщении; складываем туда,
        // чтобы вырезанное пережило повторный разбор.
        if (message.params == null) {
            message.params = new java.util.HashMap<>();
        }
        message.params.put(KEY_TAGS, found.toString());
        // Отдаём вырезанное обратно. Отбор сообщений идёт по метке, а метки к
        // тому времени в тексте уже нет — я вырезал её раньше, чем по ней
        // отбирают, и стена опустела, хотя сообщения на месте.
        return found.toString();
    }

    /**
     * Переносит разметку через вырезанные куски.
     *
     * Отсчёты считаем от конца к началу — так каждый следующий вырез не
     * сдвигает те, что ещё не обработаны. Разметку, целиком попавшую в
     * вырезанное, выбрасываем: она относилась к тому, чего больше нет.
     */
    private static ArrayList<TLRPC.MessageEntity> shift(
            ArrayList<TLRPC.MessageEntity> entities, java.util.List<int[]> cuts) {
        if (entities == null || entities.isEmpty()) {
            return entities;
        }
        final ArrayList<TLRPC.MessageEntity> out = new ArrayList<>();
        for (TLRPC.MessageEntity entity : entities) {
            int from = entity.offset;
            int to = entity.offset + entity.length;
            boolean gone = false;
            for (int i = cuts.size() - 1; i >= 0; i--) {
                final int cutFrom = cuts.get(i)[0];
                final int cutTo = cuts.get(i)[1];
                if (from >= cutFrom && to <= cutTo) {
                    gone = true;      // разметка была на самой метке
                    break;
                }
                final int size = cutTo - cutFrom;
                if (from >= cutTo) {
                    from -= size;
                    to -= size;
                } else if (to > cutFrom) {
                    // Пересеклись частично: оставляем то, что уцелело.
                    to -= Math.min(size, to - cutFrom);
                    if (from > cutFrom) {
                        from = cutFrom;
                    }
                }
            }
            if (gone || to <= from) {
                continue;
            }
            entity.offset = from;
            entity.length = to - from;
            out.add(entity);
        }
        return out;
    }

    /** Пишем ли мы сейчас на чью-то стену. */
    public static boolean writing() {
        return wallPeer != 0;
    }

    private static AccountInstance account() {
        return AccountInstance.getInstance(UserConfig.selectedAccount);
    }

    /**
     * Находит группу по имени. Ответ приходит в главный поток.
     *
     * Найденное запоминаем: имя в адрес переводится запросом к серверу, и
     * делать его на каждый открытый профиль — то же самое, что опрашивать
     * впустую, от чего мы уходили в плагинах.
     */
    public static void resolve(Peer done) {
        if (groupId == 0) {
            groupId = MargeletConfig.prefs().getLong(KEY_GROUP, 0);
        }
        if (groupId != 0) {
            done.onPeer(groupId);
            return;
        }
        synchronized (waiting) {
            waiting.add(done);
            if (resolving) {
                // Уже выясняем. Раньше здесь стоял тихий return, и второй
                // спросивший не получал ответа вовсе: открытый профиль
                // забирал ответ себе, а стена ждала его вечно и показывала
                // пустоту. Теперь ждут все и ответ получают все.
                return;
            }
            resolving = true;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                account().getMessagesController().getUserNameResolver().resolve(USERNAME,
                        id -> answer(id == null ? 0 : id));
            } catch (Throwable t) {
                FileLog.e(t);
                answer(0);
            }
        });
    }

    /** Раздать выясненный адрес всем, кто его ждал. */
    private static void answer(long id) {
        final List<Peer> ready;
        synchronized (waiting) {
            resolving = false;
            if (id != 0) {
                groupId = id;
                MargeletConfig.prefs().edit().putLong(KEY_GROUP, id).apply();
            }
            ready = new ArrayList<>(waiting);
            waiting.clear();
        }
        for (Peer peer : ready) {
            try {
                peer.onPeer(id);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    /**
     * Ищет в группе сообщения с этой меткой.
     *
     * Ищет сервер, а не телефон: своими силами пришлось бы выкачать всю
     * группу. Метка с подчёркиваниями телеграму подходит — он считает её одним
     * тегом целиком, значит совпадение точное, а не «где-то встретилось».
     */
    public static void find(String tag, int limit, Messages done) {
        find(tag, 0, limit, done);
    }

    /**
     * Ищет в группе сообщения с этой меткой, при желании — только от одного
     * человека.
     *
     * Ищет сервер, а не телефон: своими силами пришлось бы выкачать всю
     * группу. Но на сервер одного полагаться нельзя — его поиск разбивает
     * слова по подчёркиваниям, и метка вида {@code #margy_wall_123} для него
     * не одно слово, а три. Поэтому найденное мы ещё и перепроверяем сами, а
     * если поиск не вернул ничего — дочитываем свежую историю группы руками.
     *
     * {@code from} — чьи сообщения нужны, ноль значит чьи угодно. Для баннера
     * это важно: метка у баннеров одна на всех, и без этого пришлось бы
     * тащить чужие и отбирать свой.
     */
    public static void find(String tag, long from, int limit, Messages done) {
        resolve(dialogId -> {
            if (dialogId == 0) {
                done.onMessages(new ArrayList<>(), "группа не нашлась");
                return;
            }
            final MessagesController controller = account().getMessagesController();
            final TLRPC.InputPeer peer = controller.getInputPeer(dialogId);
            if (peer == null) {
                done.onMessages(new ArrayList<>(), "группа не открывается");
                return;
            }
            // Два запроса разом, а не один за другим.
            //
            // Поиск на сервере знает всю группу, но свежее сообщение попадает в
            // него не сразу: пока сервер его разберёт, только что написанного
            // на стене нет. Чтение истории видит свежее мгновенно, но дальше
            // сотни сообщений не заглядывает. Порознь каждый способ даёт либо
            // «нового не видно», либо «старого не видно»; последовательно —
            // складывает обе задержки. Вместе они закрывают друг друга, и
            // ответ приходит за время самого быстрого из них.
            final Wait wait = new Wait(2, done);
            search(peer, tag, from, limit, wait);
            history(peer, tag, from, limit, wait);
        });
    }

    /**
     * Складывает ответы двух запросов в один.
     *
     * Отдаём найденное, как только пришёл первый непустой ответ, — и потом ещё
     * раз, когда придёт второй, если он что-то добавил. Ждать оба ради полноты
     * значило бы ждать медленный там, где быстрый уже всё принёс.
     */
    private static class Wait {
        private final Messages done;
        private final List<MessageObject> all = new ArrayList<>();
        private final java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        private int left;
        private String problem;

        Wait(int count, Messages done) {
            this.left = count;
            this.done = done;
        }

        void add(List<MessageObject> found, String why) {
            left--;
            if (why != null) {
                problem = why;
            }
            boolean fresh = false;
            for (MessageObject message : found) {
                if (seen.add(message.getId())) {
                    all.add(message);
                    fresh = true;
                }
            }
            if (fresh) {
                // Новое сверху: номер сообщения растёт со временем.
                java.util.Collections.sort(all, (a, b) -> b.getId() - a.getId());
            }
            if (fresh || left == 0) {
                done.onMessages(new ArrayList<>(all), all.isEmpty() ? problem : null);
            }
        }
    }

    /** Поиск на сервере: знает всё, но свежее видит с задержкой. */
    private static void search(TLRPC.InputPeer peer, String tag, long from, int limit, Wait wait) {
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = peer;
        if (from != 0) {
            req.from_id = account().getMessagesController().getInputPeer(from);
        }
        req.q = tag;
        req.limit = limit;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        account().getConnectionsManager().sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (error != null) {
                        FileLog.e("margy: поиск в группе не вышел: " + error.text);
                        wait.add(new ArrayList<>(), error.text);
                        return;
                    }
                    wait.add(collect(response, tag, from), null);
                }));
    }

    /** Свежая история: видит только что написанное, но недалеко вглубь. */
    private static void history(TLRPC.InputPeer peer, String tag, long from, int limit, Wait wait) {
        final TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = peer;
        req.limit = Math.max(limit, 100);
        account().getConnectionsManager().sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (error != null) {
                        FileLog.e("margy: история группы не пришла: " + error.text);
                        wait.add(new ArrayList<>(), error.text);
                        return;
                    }
                    wait.add(collect(response, tag, from), null);
                }));
    }

    /**
     * Отбирает из ответа то, что нам действительно подходит.
     *
     * Проверка метки здесь, а не только на сервере, потому что серверный поиск
     * приблизительный: на {@code #margy_wall_5} он охотно вернёт и
     * {@code #margy_wall_7}. Стена чужого человека, показанная у себя, была бы
     * хуже пустой стены.
     */
    private static List<MessageObject> collect(TLObject response, String tag, long from) {
        final List<MessageObject> out = new ArrayList<>();
        if (!(response instanceof TLRPC.messages_Messages)) {
            return out;
        }
        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
        account().getMessagesController().putUsers(res.users, false);
        account().getMessagesController().putChats(res.chats, false);
        for (TLRPC.Message message : res.messages) {
            if (message == null || !hasTag(message, tag)) {
                continue;
            }
            final MessageObject object =
                    new MessageObject(UserConfig.selectedAccount, message, true, true);
            if (from != 0 && authorOf(object) != from) {
                continue;
            }
            out.add(object);
        }
        return out;
    }

    /** Есть ли ровно эта метка среди вырезанных. */
    private static boolean contains(String cut, String tag) {
        if (cut == null || cut.isEmpty()) {
            return false;
        }
        int at = cut.indexOf(tag);
        while (at >= 0) {
            final int end = at + tag.length();
            // Метки разделены пробелом, поэтому «ровно эта» — значит следом
            // пробел или конец: иначе wall_5 совпал бы с wall_55.
            if (end >= cut.length() || cut.charAt(end) == ' ') {
                return true;
            }
            at = cut.indexOf(tag, at + 1);
        }
        return false;
    }

    /**
     * Стоит ли в сообщении ровно эта метка.
     *
     * Ровно — значит следом не идёт ни буквы, ни цифры, ни подчёркивания:
     * иначе {@code #margy_wall_5} совпал бы с {@code #margy_wall_55}.
     */
    private static boolean hasTag(TLRPC.Message message, String tag) {
        final String text = message.message;
        if (text == null) {
            return false;
        }
        int at = text.indexOf(tag);
        while (at >= 0) {
            final int end = at + tag.length();
            if (end >= text.length()) {
                return true;
            }
            final char next = text.charAt(end);
            if (!Character.isLetterOrDigit(next) && next != '_') {
                return true;
            }
            at = text.indexOf(tag, at + 1);
        }
        return false;
    }

    /**
     * Пишет в группу от имени человека.
     *
     * Именно от имени человека, а не от бота: на этом и держится вся затея.
     * Подпись под сообщением ставит телеграм, и она же отвечает на вопрос,
     * чей это баннер и кто написал на стене.
     */
    public static void post(String text, Runnable done) {
        resolve(dialogId -> {
            if (dialogId == 0) {
                if (done != null) {
                    done.run();
                }
                return;
            }
            try {
                final SendMessagesHelper.SendMessageParams params =
                        SendMessagesHelper.SendMessageParams.of(text, dialogId);
                account().getSendMessagesHelper().sendMessage(params);
            } catch (Throwable t) {
                FileLog.e(t);
            }
            if (done != null) {
                done.run();
            }
        });
    }

    /** Убрать своё сообщение из группы. Чужие удалять нечем — и не надо. */
    public static void remove(int messageId) {
        resolve(dialogId -> {
            if (dialogId == 0 || messageId == 0) {
                return;
            }
            try {
                final ArrayList<Integer> ids = new ArrayList<>();
                ids.add(messageId);
                // Ноль в конце — обычный режим переписки, тот же, каким
                // телеграм удаляет сообщения из своего же экрана чата.
                account().getMessagesController().deleteMessages(ids, null, null, dialogId,
                        0, true, 0);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    /** Тот ли это человек, чьё сообщение мы смотрим. */
    public static long authorOf(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return 0;
        }
        try {
            return MessageObject.getFromChatId(message.messageOwner);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Пропускаем ли это сообщение на показ.
     *
     * Проверка стоит на показе, а не только на отправке, и это не
     * перестраховка: написать в группу можно обычным телеграмом, мимо нашего
     * приложения. Тогда сообщение в группе останется, а на стене его не будет.
     */
    public static boolean showable(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return false;
        }
        final CharSequence text = message.messageOwner.message;
        final List<String> hidden = new ArrayList<>();
        try {
            if (message.messageOwner.entities != null) {
                for (TLRPC.MessageEntity entity : message.messageOwner.entities) {
                    if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                        hidden.add(((TLRPC.TL_messageEntityTextUrl) entity).url);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return MargeletLinks.clean(text, hidden);
    }
}
