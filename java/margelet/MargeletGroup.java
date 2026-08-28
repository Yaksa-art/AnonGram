package org.telegram.margelet;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
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
        return "#margy_wall_" + peerId;
    }

    private static long groupId;
    private static boolean resolving;

    public interface Peer {
        void onPeer(long dialogId);
    }

    public interface Messages {
        /** Найденное, новое сверху. Пустой список — не нашли или не смогли. */
        void onMessages(List<MessageObject> messages);
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
        if (groupId != 0) {
            done.onPeer(groupId);
            return;
        }
        if (resolving) {
            return;
        }
        resolving = true;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                account().getMessagesController().getUserNameResolver().resolve(USERNAME, id -> {
                    resolving = false;
                    if (id != null && id != 0) {
                        groupId = id;
                        done.onPeer(groupId);
                    } else {
                        done.onPeer(0);
                    }
                });
            } catch (Throwable t) {
                FileLog.e(t);
                resolving = false;
                done.onPeer(0);
            }
        });
    }

    /**
     * Ищет в группе сообщения с этой меткой.
     *
     * Ищет сервер, а не телефон: своими силами пришлось бы выкачать всю
     * группу. Метка с подчёркиваниями телеграму подходит — он считает её одним
     * тегом целиком, значит совпадение точное, а не «где-то встретилось».
     */
    public static void find(String tag, int limit, Messages done) {
        resolve(dialogId -> {
            if (dialogId == 0) {
                done.onMessages(new ArrayList<>());
                return;
            }
            final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = account().getMessagesController().getInputPeer(dialogId);
            if (req.peer == null) {
                done.onMessages(new ArrayList<>());
                return;
            }
            req.q = tag;
            req.limit = limit;
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            account().getConnectionsManager().sendRequest(req, (response, error) ->
                    AndroidUtilities.runOnUIThread(() -> {
                        final List<MessageObject> out = new ArrayList<>();
                        if (error == null && response instanceof TLRPC.messages_Messages) {
                            final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                            account().getMessagesController().putUsers(res.users, false);
                            account().getMessagesController().putChats(res.chats, false);
                            for (TLRPC.Message message : res.messages) {
                                if (message == null) {
                                    continue;
                                }
                                out.add(new MessageObject(UserConfig.selectedAccount, message, true, true));
                            }
                        } else if (error != null) {
                            FileLog.e("margy: поиск в группе не вышел: " + error.text);
                        }
                        done.onMessages(out);
                    }));
        });
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
