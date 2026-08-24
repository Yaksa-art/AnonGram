# Margelet 插件

[English](plugins.md) · [Русский](plugins.ru.md) · **中文**

插件是在这个分支内部运行的 Python 代码。它不是独立的程序，也不是外部的机器人：
它就住在你聊天所在的那个应用里。

## 论坛的规矩

**插件代码不得混淆。** 它以源码形式分发，任何人都应能打开来看。混淆＝论坛封禁。

原因很直白：这里没有沙箱。想知道一个插件到底做了什么，唯一的办法就是读它的代码。
藏起来的代码，等于一次性剥夺了所有人的这个办法。

## 关于安全，说实话

应用能做的，插件都能做：读你的聊天、以你的名义发消息、翻你的文件。清单里的权限
只是**作者的声明**，不是限制；应用不会核实，也无法核实。

只安装你自己读过的，或者你信任的。

## .marp 格式

就是一个改名成 `.marp` 的普通 zip：

```
margelet_example.marp
├── manifest.json   必需
├── main.py         必需
├── icon.png        可选
└── ...             插件需要的其它文件
```

`icon.png` 会显示在插件列表里。方形图片，128×128 就够了。

## manifest.json

```json
{
  "id": "margelet.example",
  "name": "示例",
  "version": "1.0",
  "author": "narezany",
  "description": "在控制台里打个招呼。",
  "min_version": "0.3",
  "permissions": ["ui"]
}
```

| 字段 | 含义 |
|---|---|
| `id` | 插件编号。拉丁字母加点。更新和设置都按它来存，不要改。 |
| `name` | 列表里显示的名字。 |
| `version` | 版本，字符串。 |
| `author` | 作者。 |
| `description` | 一两句话：它做什么。 |
| `min_version` | 插件能运行的最低 Margelet 版本。版本更旧就根本装不上——并且会说明原因，而不是默默失败。可不填。 |
| `permissions` | 插件对自己的声明。见下表。 |
| `name_en`、`name_zh`、`description_en`… | 同一字段的其它语言版本。应用按自己的语言取，没有对应翻译就用原字段。 |

权限：`read_chats`、`send_messages`、`edit_messages`、`delete_messages`、
`change_profile`、`ui`。也可以写自定义名称——它会原样显示。

## main.py

```python
def on_start():
    margelet.log("插件在此问好", margelet.name)
```

插件启动时会调用 `on_start()`。没有它，插件就从上到下直接执行一遍。

`margelet` 对象无需 import 即可使用：

| | |
|---|---|
| `margelet.id` | 清单里的编号 |
| `margelet.name` | 清单里的名字 |
| `margelet.folder` | 插件在手机上的文件夹 |
| `margelet.log(*部分)` | 往控制台写一行 |
| `margelet.error(*部分)` | 同上，红色 |
| `margelet.ui(调用, delay_ms=0)` | 在主线程上执行——凡是碰屏幕的都必须如此 |
| `margelet.every(毫秒, 调用)` | 每隔这么久重复一次，返回一个句柄 |
| `margelet.cancel(句柄)` | 停止重复 |
| `margelet.toast(文本)` | 屏幕上的一行短提示 |
| `margelet.get(键, 默认=None)` | 插件自己的记忆 |
| `margelet.set(键, 值)` | 写入其中 |
| `margelet.flag(键, 默认=False)` | 把设置界面上的开关读成是/否 |

`get` 与 `set` 既能挺过重启，也能挺过插件自身的更新：它们不放在插件目录里，
而目录在更新时会被替换。

## 事件

不是插件去问应用，而是应用来叫插件。

| | |
|---|---|
| `margelet.on_chat_opened(调用)` | 打开了聊天，并把该界面交给你 |
| `margelet.on_send(调用)` | 有人要发文本，在发出去之前 |
| `margelet.on_message(调用)` | 来了一条消息 |
| `margelet.button(标题, 调用)` | 在聊天菜单（三个点）里加自己的一行 |
| `margelet.on_settings(调用)` | 有人改了本插件的某项设置 |

门是有意留得少的，而且每一扇都有名字。这跟“让插件替换应用的任意方法”不是
一回事：替换任意方法等于在运行时改写别人的代码，得靠一个专门改机器码的库，
而 Telegram 每更新一次，写在上面的东西就全废。有名字的门能挺过更新，因为
守着它的是我们，不是名字的偶然相同。

需要的门这里没有，就[去论坛说](https://t.me/margeletforum)。我们会加一扇有
名字的，而不是把所有门一次性打开。

### 打开了聊天

```python
def on_start():
    margelet.on_chat_opened(sit_on_the_box)

def sit_on_the_box(chat):
    box = chat.getChatActivityEnterView()
    ...
```

每次聊天界面出现时都会调用，并把那个界面交给你。

### 发送

```python
def on_start():
    margelet.on_send(sign)

def sign(text, chat):
    if text.startswith("/"):
        return False          # 干脆不发
    return text + " 🌿"       # 发这个
```

返回什么：字符串——发出去的就是它；`False`——不发；什么都不返回——原样发。
若有多个插件订阅，会依次调用，每个看到的都是上一个改过之后的文本。

这是应用唯一会**等**的事件：处理函数在想的时候，人正盯着还没发出去的消息。
耗时的活儿要挪到 `margelet.ui` 或 `margelet.every` 里去。处理函数要是想了
超过十分之一秒，控制台会说一声——不是责备，是让作者知道。

### 消息到来

```python
def on_start():
    margelet.on_message(count)

def count(text, chat, message_id, mine):
    if not mine:
        margelet.log("来了：", text)
```

自己发出去的消息也会到这里——`mine` 就是用来区分的。返回值不起作用：消息
已经到了。

### 聊天里属于自己的按钮

```python
def on_start():
    margelet.button("数一数", count)

def count(chat):
    margelet.toast("这里有 " + str(chat.getMessagesCount()) + " 条消息")
```

这一行排在聊天菜单最后，在所有常规条目之后：别人的代码不该把熟悉的条目挤开。

某个插件的回调抛错不会连累其他插件：每个都单独调用，出错的那个会在控制台里
拿到自己的堆栈。

`print()` 也会进控制台——它被接管了。

## 属于自己的设置界面

插件不自己画界面——它只说界面由什么组成，画由应用来画。所以插件里的开关和
别处的开关一模一样：同一套主题、同一种颜色、同样的点法。

```python
def on_start():
    margelet.settings(
        margelet.header("怎么问好"),
        margelet.switch("hello", "问好", default=True,
                        about="打开聊天时说一句你好。"),
        margelet.text("name", "名字", default="朋友"),
        margelet.choice("mood", "语气", ["轻快", "平静"]),
        margelet.note("这些都留在手机上，哪儿也不去。"),
        margelet.action("全部忘掉", forget, danger=True),
    )
    margelet.on_settings(changed)

def changed(key, value):
    margelet.log("现在", key, "=", value)

def forget():
    margelet.toast("忘了")
```

| 行 | 含义 |
|---|---|
| `margelet.header(文本)` | 分组标题 |
| `margelet.note(文本)` | 灰色的说明 |
| `margelet.switch(键, 标题, default=False, about=None)` | 开关；用 `margelet.flag(键)` 读 |
| `margelet.text(键, 标题, default="", about=None)` | 手动填的一行；用 `margelet.get(键)` 读 |
| `margelet.choice(键, 标题, 选项, default=None)` | 多选一 |
| `margelet.action(标题, 调用, danger=False)` | 只干一件事的按钮 |

`settings()` 在启动时调用一次。默认值会立刻写进去——否则明明谁也没改，第一次
读却是空的。

有设置的插件会在列表里出现一个齿轮。点齿轮左边的那一行进设置，点右边的开关
则是开关插件本身。

这份声明和插件的记忆存在一起，而不是留在内存里，所以关着的插件也能打开设置
界面：有时正是要先把设置改好，再打开插件。

## 除此之外还能用什么

这里的 Python 是真的 Python，能访问应用的 Java 类：

```python
from java import jclass

Config = jclass("org.telegram.margelet.MargeletConfig")
margelet.log("水印：", Config.watermarkOnSend())
```

顺着它就能摸到应用的其余部分。所谓“插件什么都能做”，不是修辞。

## 控制台

设置 → Margelet → 插件 → 控制台。插件打印的一切都进这里，错误是红色的。
Python 出错时是沉默的，没有这个界面，作者只能从“怎么什么都不动”里猜自己的笔误。

## 安装

设置 → Margelet → 插件 → 从文件安装。安装窗口会显示作者和声明的权限。

也可以直接在聊天里点 `.marp` 文件——应用会提示安装。

安装窗口有两个按钮。“安装”装上但保持关闭。“安装并启用”装上、打开，并立刻
重启 Margelet——插件马上就开始工作，不必等着手动关掉应用。

如果插件声明的 `min_version` 高于你的版本，它就装不上，并会告诉你需要哪个
版本——而不是装上了再坏掉。

长按那一行看插件详情和删除。

关闭的意思是“不再启动它”。已经跑起来的 Python 代码没有办法停下——它会活到应用
重启为止。插件界面上的“重启 Margelet”按钮就是为此：关掉、点一下，插件就没了。

## 示例

[margelet_example.marp](margelet_example.marp) —— 和应用里自带的是同一个。
里头就两行，从它开始正好。

## 有问题去哪

[论坛](https://t.me/margeletforum) · [频道](https://t.me/margeletter)
