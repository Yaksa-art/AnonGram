package org.telegram.ui;

import android.os.Bundle;

import org.telegram.margelet.MargeletGroup;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

/**
 * Стена: что о человеке написали другие.
 *
 * Дуров стену убрал, здесь она возвращается — и не как страница, которую
 * хозяин правит под себя. Смысл ровно в обратном: написанное про тебя ты
 * снять не можешь. Поэтому стена и работает против разводил — обманутый
 * пишет, обманщик не стирает, а видят все.
 *
 * Это обычный экран переписки, у которого убрано всё, кроме сообщений с
 * меткой этой стены. Не похожий на него, не собранный из его деталей и не
 * его режим поиска — он сам. Отбор стоит внутри {@link ChatActivity}, на
 * входе списка сообщений; всё остальное — список, поле ввода, меню
 * сообщения, реакции — работает ровно так же, как в любой переписке.
 *
 * Дошло до этого с четвёртого раза. Сначала свои карточки, потом чужие
 * ячейки на своём экране, потом чужой экран с включённым поиском, который не
 * отбирает сообщения, а подсвечивает их, потом экран результатов поиска —
 * список строчек вместо переписки. Каждый раз это выглядело работой, и
 * каждый раз владелец видел подделку раньше меня. Он же и сказал, как надо:
 * взять стандартный чат и изменить его.
 */
public class MargeletWallActivity extends ChatActivity {

    private final long peerId;
    private final String peerName;

    private MargeletWallActivity(Bundle args, long peerId, String peerName) {
        super(args);
        this.peerId = peerId;
        this.peerName = peerName == null ? "" : peerName;
    }

    /**
     * Открыть чью-то стену.
     *
     * Через статический метод, а не через конструктор: адрес группы сперва
     * надо выяснить, а это поездка на сервер. Конструктор, умеющий ждать,
     * обманывает вызывающего — он вернёт экран, который ещё не знает, что
     * показывать.
     */
    public static void open(BaseFragment from, long peerId, String peerName) {
        if (from == null || peerId == 0) {
            return;
        }
        MargeletGroup.resolve(dialogId -> {
            if (dialogId == 0) {
                BulletinFactory.of(from).createSimpleBulletin(R.raw.error,
                        LocaleController.getString(R.string.MargeletGroupUnreachable)).show();
                return;
            }
            final Bundle args = new Bundle();
            // Группа приходит номером переписки — со знаком минус и приставкой
            // канала; ChatActivity ждёт голый номер чата.
            args.putLong("chat_id", -dialogId);
            args.putString("margeletWallTag", MargeletGroup.tagWall(peerId));
            args.putString("margeletWallName", peerName == null ? "" : peerName);
            args.putLong("margeletWallPeer", peerId);
            from.presentFragment(new MargeletWallActivity(args, peerId, peerName));
        });
    }

    @Override
    public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) {
            return false;
        }
        // Пока экран открыт, отправка дописывает метку сама. Человек пишет в
        // обычное поле обычной переписки и про метки ничего не знает — знать
        // ему и незачем, это наша служебная разметка, а не его забота.
        MargeletGroup.writingTo(peerId);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        MargeletGroup.writingTo(peerId);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Ушли с экрана — метку больше не дописываем. Иначе она уехала бы в
        // соседнюю переписку вместе со следующим же сообщением.
        MargeletGroup.writingTo(0);
    }

    @Override
    public void onFragmentDestroy() {
        MargeletGroup.writingTo(0);
        super.onFragmentDestroy();
    }
}
