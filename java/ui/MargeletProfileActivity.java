package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.widget.LinearLayout;

import org.telegram.margelet.MargeletBanner;
import org.telegram.margelet.MargeletConfig;
import org.telegram.margelet.MargeletGradient;
import org.telegram.margelet.MargeletGroup;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Ветка «Профиль»: баннер за аватаркой и стены.
 *
 * Обе вещи живут в общей группе, а не у нас на сервере, и это стоит сказать
 * человеку прямо на экране, а не спрятать: то, что он сюда положит, увидят
 * все, включая тех, у кого форка нет.
 */
public class MargeletProfileActivity extends UniversalFragment {

    private static final int ID_BANNER = 1;
    private static final int ID_BANNER_OFF = 2;
    private static final int ID_BANNERS_SHOW = 3;
    private static final int ID_WALL_SHOW = 4;
    private static final int ID_MY_WALL = 5;
    private static final int ID_GROUP = 6;
    private static final int ID_GRADIENT = 7;
    private static final int ID_GRADIENT_OFF = 8;
    private static final int ID_GRADIENTS_SHOW = 9;

    /**
     * С чего начинается выбор, если своего градиента ещё нет.
     *
     * Те же два цвета, что у значков форка: зелёный и лавандовый. Начинать с
     * чёрного и белого — значит показать человеку не градиент, а полосу.
     */
    private static final int ЗЕЛЁНЫЙ = 0xFF8DD1B0;
    private static final int ЛАВАНДА = 0xFFB7A8E0;

    private static final int PICK_BANNER = 4833;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletProfileTitle);
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.MargeletBannerHeader)));
        items.add(UItem.asButton(ID_BANNER, LocaleController.getString(R.string.MargeletBannerPick)));
        items.add(UItem.asButton(ID_BANNER_OFF, LocaleController.getString(R.string.MargeletBannerRemove)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletBannerAbout)));

        items.add(UItem.asCheck(ID_BANNERS_SHOW, LocaleController.getString(R.string.MargeletBannerShow))
                .setChecked(MargeletConfig.bannersEnabled()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MargeletGradientHeader)));
        items.add(UItem.asButton(ID_GRADIENT, LocaleController.getString(R.string.MargeletGradientPick)));
        items.add(UItem.asButton(ID_GRADIENT_OFF, LocaleController.getString(R.string.MargeletGradientRemove)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletGradientAbout)));

        items.add(UItem.asCheck(ID_GRADIENTS_SHOW, LocaleController.getString(R.string.MargeletGradientShow))
                .setChecked(MargeletConfig.gradientsEnabled()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MargeletWall)));
        items.add(UItem.asButton(ID_MY_WALL, LocaleController.getString(R.string.MargeletWallOpenMine)));
        items.add(UItem.asCheck(ID_WALL_SHOW, LocaleController.getString(R.string.MargeletWallShow))
                .setChecked(MargeletConfig.wallEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletWallAbout)));

        items.add(UItem.asButton(ID_GROUP, LocaleController.getString(R.string.MargeletProfileGroup),
                "@" + MargeletGroup.USERNAME));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletProfileGroupAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_BANNER) {
            pick();
        } else if (item.id == ID_BANNER_OFF) {
            MargeletBanner.clear(what -> {
                listView.adapter.update(true);
                if (what == MargeletGroup.REMOVED) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                            LocaleController.getString(R.string.MargeletBannerRemoved)).show();
                } else if (what == MargeletGroup.NOTHING) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                            LocaleController.getString(R.string.MargeletBannerNone)).show();
                } else {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                            LocaleController.getString(R.string.MargeletGroupUnreachable)).show();
                }
            });
        } else if (item.id == ID_GRADIENT) {
            pickGradient();
        } else if (item.id == ID_GRADIENT_OFF) {
            MargeletGradient.clear(what -> {
                listView.adapter.update(true);
                if (what == MargeletGroup.REMOVED) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                            LocaleController.getString(R.string.MargeletGradientRemoved)).show();
                } else if (what == MargeletGroup.NOTHING) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                            LocaleController.getString(R.string.MargeletGradientNone)).show();
                } else {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                            LocaleController.getString(R.string.MargeletGroupUnreachable)).show();
                }
            });
        } else if (item.id == ID_GRADIENTS_SHOW) {
            MargeletConfig.setGradientsEnabled(!MargeletConfig.gradientsEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_BANNERS_SHOW) {
            MargeletConfig.setBannersEnabled(!MargeletConfig.bannersEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_WALL_SHOW) {
            MargeletConfig.setWallEnabled(!MargeletConfig.wallEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_MY_WALL) {
            final long me = UserConfig.getInstance(currentAccount).getClientUserId();
            MargeletWallActivity.open(this, me,
                    LocaleController.getString(R.string.MargeletWallMine));
        } else if (item.id == ID_GROUP) {
            Browser.openUrl(getContext(), "https://t.me/" + MargeletGroup.USERNAME);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    /**
     * Образец: тот же радиальный градиент, каким телеграм красит шапку профиля.
     *
     * Считается по тем же правилам, что и там: второй цвет в середине, первый
     * по краю. Иначе человек выбирал бы вслепую и получал не то, что видел —
     * а это худший род сюрприза, потому что винить он будет себя.
     */
    private static final class GradientPreview extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color1, color2;
        private int builtFor1, builtFor2, builtWidth, builtHeight;

        GradientPreview(Context context, int color1, int color2) {
            super(context);
            this.color1 = color1;
            this.color2 = color2;
        }

        void setColors(int color1, int color2) {
            this.color1 = color1;
            this.color2 = color2;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int width = getWidth();
            final int height = getHeight();
            if (width == 0 || height == 0) {
                return;
            }
            if (paint.getShader() == null || builtFor1 != color1 || builtFor2 != color2
                    || builtWidth != width || builtHeight != height) {
                builtFor1 = color1;
                builtFor2 = color2;
                builtWidth = width;
                builtHeight = height;
                paint.setShader(new RadialGradient(width / 2f, height * 0.1f,
                        Math.max(width, height) * 0.9f,
                        new int[]{color2, color1}, new float[]{0, 1}, Shader.TileMode.CLAMP));
            }
            canvas.drawRect(0, 0, width, height, paint);
        }
    }

    /**
     * Выбрать себе градиент.
     *
     * Предупреждаем до выбора, а не после отправки — по той же причине, что и
     * с баннером: пара цветов уходит в общую группу отдельным сообщением, и
     * видно её всем. Разница только в том, что уходит не картинка, а строка.
     */
    private void pickGradient() {
        final Context context = getContext();
        if (context == null) {
            return;
        }
        final long me = UserConfig.getInstance(currentAccount).getClientUserId();
        final int[] mine = MargeletGradient.of(me, null);
        final int[] chosen = mine != null
                ? new int[]{mine[0], mine[1]}
                : new int[]{ЗЕЛЁНЫЙ, ЛАВАНДА};

        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);

        final GradientPreview preview = new GradientPreview(context, chosen[0], chosen[1]);
        box.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 96));

        final ColorPicker picker = new ColorPicker(context, false,
                (color, num, applyNow) -> {
                    // Номер приходит от самого выбиральщика: ноль — первый
                    // кружок, единица — второй. Чужие номера игнорируем, а не
                    // складываем в тот же массив: их там просто нет.
                    if (num >= 0 && num < chosen.length) {
                        chosen[num] = 0xFF000000 | (color & 0xFFFFFF);
                        preview.setColors(chosen[0], chosen[1]);
                    }
                }) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY));
            }
        };
        picker.setColor(chosen[0], 0);
        picker.setColor(chosen[1], 1);
        // Ровно два цвета, и добавить третий нельзя: под шапкой профиля у
        // телеграма радиальный градиент на две точки, и третий он не нарисует.
        picker.setType(-1, true, 2, 2, false, 0, false);
        box.addView(picker, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.MargeletGradientHeader))
                .setView(box)
                .setPositiveButton(LocaleController.getString(R.string.MargeletGradientSave), (d, w) ->
                        MargeletGradient.set(chosen[0], chosen[1], () -> {
                            if (getContext() == null) {
                                return;
                            }
                            listView.adapter.update(true);
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                                    LocaleController.getString(R.string.MargeletGradientSaved)).show();
                        }))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void pick() {
        // Предупреждаем до выбора, а не после отправки: баннер уходит в общую
        // группу, и оттуда его видно всем, даже тем, у кого форка нет. Человек
        // должен знать это раньше, чем выберет фотографию.
        new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.MargeletBannerHeader))
                .setMessage(LocaleController.getString(R.string.MargeletBannerWarn))
                .setPositiveButton(LocaleController.getString(R.string.MargeletBannerPick), (d, w) -> {
                    final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    try {
                        startActivityForResult(intent, PICK_BANNER);
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_BANNER || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        MargeletBanner.set(uri, () -> {
            if (getContext() == null) {
                return;
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    LocaleController.getString(R.string.MargeletBannerSaved)).show();
        });
    }
}
