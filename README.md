# Margelet

Форк телеграма для андроида. Основан на [DrKLO/Telegram](https://github.com/DrKLO/Telegram),
собирается из его исходников с наложением патча из этого репозитория.

Пакет: `cat.narezany.margelet`. Ставится рядом с обычным телеграмом, не поверх.

## Что здесь лежит

- `patch/margelet.patch` — все правки к исходникам телеграма одним файлом.
- `patch/UPSTREAM` — коммит телеграма, к которому патч приложен и на котором проверен.
- `java/` — файлы, которые патч добавляет целиком (их же можно просто скопировать).
- `res/` — своя иконка и звук.
- `tools/` — скрипты: рисование иконки, подстановка её в ресурсы, синтез звука.
- `FEATURES.md` — **главный файл**. Полный список того, что форк меняет, и где
  именно. По нему форк переносится на новую версию телеграма, когда патч
  перестанет накладываться.

## Чего здесь нет и не будет

- **api_id и api_hash.** Это личные ключи владельца сборки, выданные на
  my.telegram.org. Свои получаешь там же и вписываешь в `BuildVars.java`
  локально. В репозиторий они не кладутся никогда.
- **google-services.json.** Тот, что лежит в исходниках телеграма, описывает их
  firebase-проект, и нашего пакета в нём нет. Пока у форка нет своего файла,
  плагин `com.google.gms.google-services` отключён, а пуши в фоне не работают.

## Сборка

Нужны SDK 35 с build-tools 35.0.0, NDK 27.2.12479018, JDK 21.

```
git clone https://github.com/DrKLO/Telegram
cd Telegram
git checkout <коммит из patch/UPSTREAM>
git submodule update --init --recursive
git apply ../margelet/patch/margelet.patch
# вписать свои api_id и api_hash в
# TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java
gradle :TMessagesProj_AppStandalone:assembleAfatStandalone
```

Готовый apk окажется в
`TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/`.

## Лицензия

Исходники телеграма распространяются под GPL v2 или новее. Форк наследует эту
лицензию: если раздаёшь собранный apk — обязан дать и исходники, из которых он
собран. Этот репозиторий вместе с коммитом из `patch/UPSTREAM` как раз ими и
является.

Звук мяуканья — не наш, но и не ничей: см. `ATTRIBUTION.md`.
