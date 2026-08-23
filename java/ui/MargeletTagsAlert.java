package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.margelet.MargeletTags;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

/**
 * Правка тегов у аудио прямо из чата: название, исполнитель, обложка.
 *
 * В телеграме это обычно делают через ботов — то есть отдают свой файл чужому
 * серверу ради трёх строчек текста. Здесь всё происходит на телефоне: теги
 * пишутся в копию файла, копия отправляется в тот же чат. Исходное сообщение
 * не трогаем: чужое сообщение править нельзя, а своё телеграм разрешает менять
 * только текстом, не файлом.
 *
 * Обложка выбирается телеграмовским выбором фотографий и кадрируется его же
 * экраном — тем самым, что режет аватарки. Системный выбор файлов, стоявший
 * здесь сначала, выглядел чужеродно, а обрезать в нём было нечем.
 */
public class MargeletTagsAlert {

    /** Живой разбор переживает открытие выбора фото: экран уходит и приходит. */
    private static MargeletTagsAlert current;

    private final ChatActivity fragment;
    private final MessageObject message;

    private String title = "";
    private String artist = "";
    private byte[] cover;
    private Bitmap coverPreview;

    private EditTextBoldCursor titleField;
    private EditTextBoldCursor artistField;
    private ImageView coverView;
    private TextView coverHint;

    private MargeletTagsAlert(ChatActivity fragment, MessageObject message) {
        this.fragment = fragment;
        this.message = message;
        final TLRPC.Document document = message.getDocument();
        if (document != null) {
            for (TLRPC.DocumentAttribute a : document.attributes) {
                if (a instanceof TLRPC.TL_documentAttributeAudio) {
                    title = a.title == null ? "" : a.title;
                    artist = a.performer == null ? "" : a.performer;
                }
            }
        }
    }

    public static void show(ChatActivity fragment, MessageObject message) {
        if (fragment == null || fragment.getParentActivity() == null || message == null) {
            return;
        }
        current = new MargeletTagsAlert(fragment, message);
        current.open();
    }

    private static EditTextBoldCursor field(Activity context, CharSequence hint, String value) {
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        edit.setHintText(hint == null ? "" : hint.toString());
        edit.setBackgroundDrawable(null);
        edit.setLineColors(Theme.getColor(Theme.key_dialogInputField),
                Theme.getColor(Theme.key_dialogInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular));
        edit.setSingleLine(true);
        edit.setPadding(0, dp(4), 0, dp(6));
        edit.setText(value);
        return edit;
    }

    private void open() {
        final Activity context = fragment.getParentActivity();
        if (context == null) {
            return;
        }

        // Слева квадрат обложки, справа два поля. Так видно, что получится, —
        // а не только названия полей.
        coverView = new ImageView(context);
        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverView.setBackgroundColor(Theme.getColor(Theme.key_dialogInputField));
        coverView.setOnClickListener(v -> pickCover());

        coverHint = new TextView(context);
        coverHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        coverHint.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        coverHint.setGravity(Gravity.CENTER);
        coverHint.setOnClickListener(v -> pickCover());

        titleField = field(context, LocaleController.getString(R.string.MargeletTrackTitle), title);
        artistField = field(context, LocaleController.getString(R.string.MargeletTrackArtist), artist);

        final LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(titleField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));
        fields.addView(artistField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 10, 0, 0));

        final LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(coverView, LayoutHelper.createLinear(76, 76, Gravity.TOP, 0, 6, 14, 0));
        top.addView(fields, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(coverHint, LayoutHelper.createLinear(76, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 6, 0, 0));

        updateCoverView();

        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.MargeletTrackTags))
                .setView(layout)
                .setPositiveButton(LocaleController.getString(R.string.MargeletTrackSend), (d, w) -> apply())
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void updateCoverView() {
        if (coverView == null) {
            return;
        }
        if (coverPreview != null) {
            coverView.setImageBitmap(coverPreview);
            coverHint.setText(LocaleController.getString(R.string.MargeletTrackCoverChosen));
        } else {
            coverView.setImageResource(R.drawable.msg_round_file_s);
            coverView.setColorFilter(Theme.getColor(Theme.key_dialogTextHint));
            coverHint.setText(LocaleController.getString(R.string.MargeletTrackCover));
        }
    }

    /** Запоминаем набранное: диалог сейчас закроется, а вернуться надо к нему же. */
    private void remember() {
        if (titleField != null) {
            title = titleField.getText().toString();
        }
        if (artistField != null) {
            artist = artistField.getText().toString();
        }
    }

    private void pickCover() {
        remember();
        final PhotoAlbumPickerActivity picker =
                new PhotoAlbumPickerActivity(PhotoAlbumPickerActivity.SELECT_TYPE_AVATAR, false, false, null);
        picker.setMaxSelectedPhotos(1, false);
        picker.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
                if (photos == null || photos.isEmpty()) {
                    return;
                }
                openCrop(photos.get(0));
            }

            @Override
            public void startPhotoSelectActivity() {
            }
        });
        fragment.presentFragment(picker);
    }

    private void openCrop(SendMessagesHelper.SendingMediaInfo info) {
        final Bundle args = new Bundle();
        if (info.path != null) {
            args.putString("photoPath", info.path);
        } else if (info.uri != null) {
            args.putParcelable("photoUri", info.uri);
        } else {
            return;
        }
        final PhotoCropActivity crop = new PhotoCropActivity(args);
        crop.setDelegate(bitmap -> {
            takeCover(bitmap);
            // Экран кадрирования закрывает себя сам, сразу после этого вызова.
            // Поэтому окно тегов открываем чуть позже: иначе оно всплыло бы
            // поверх уходящего экрана. Стек при этом не трогаем — вызов
            // removeSelfFromStack у fragment убрал бы сам чат, а не картинку.
            org.telegram.messenger.AndroidUtilities.runOnUIThread(this::open, 220);
        });
        fragment.presentFragment(crop, true);
    }

    private void takeCover(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        // Обложку ужимаем: в тег иногда кладут снимок с камеры на пять
        // мегабайт, и он поедет вместе с песней каждому получателю.
        final int max = 800;
        Bitmap out = bitmap;
        if (out.getWidth() > max || out.getHeight() > max) {
            final float scale = Math.min(max / (float) out.getWidth(), max / (float) out.getHeight());
            out = Bitmap.createScaledBitmap(out,
                    Math.round(out.getWidth() * scale), Math.round(out.getHeight() * scale), true);
        }
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 87, bytes);
        cover = bytes.toByteArray();
        coverPreview = out;
    }

    private void apply() {
        remember();
        final File src = FileLoader.getInstance(fragment.getCurrentAccount())
                .getPathToMessage(message.messageOwner);
        if (src == null || !src.exists()) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.MargeletTrackNeedFile)).show();
            return;
        }
        final String name = message.getDocumentName();
        final File dst = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE),
                (name == null || name.isEmpty() ? "track.mp3" : name));
        if (!MargeletTags.write(src, dst, title.trim(), artist.trim(), cover)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                    LocaleController.getString(R.string.MargeletTrackWriteFailed)).show();
            return;
        }
        SendMessagesHelper.prepareSendingDocument(fragment.getAccountInstance(),
                dst.getAbsolutePath(), dst.getAbsolutePath(), null, null, "audio/mpeg",
                fragment.getDialogId(), null, fragment.getThreadMessage(), null, null, null,
                true, 0, null, fragment.getMessageChatSendParams(), false);
        current = null;
    }
}
