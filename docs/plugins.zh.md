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
| `margelet.on_chat_opened(调用)` | 打开聊天时被调用，并把该界面交给你 |

`get` 与 `set` 既能挺过重启，也能挺过插件自身的更新：它们不放在插件目录里，
而目录在更新时会被替换。

## 事件

```python
def on_start():
    margelet.on_chat_opened(sit_on_the_box)

def sit_on_the_box(chat):
    box = chat.getChatActivityEnterView()
    ...
```

每次聊天界面出现时都会调用 `on_chat_opened`，并把那个界面交给你。在这个事件
出现之前，需要用到已打开聊天的插件只能每秒问上好几次——那是在轮询应用本身
早已知道的事，白白耗电。

某个插件的回调抛错不会连累其他插件：每个都单独调用，出错的那个会在控制台里
拿到自己的堆栈。

`print()` 也会进控制台——它被接管了。

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

新插件安装后是关闭状态。点一下开关打开，长按看详情和删除。

关闭的意思是“不再启动它”。已经跑起来的 Python 代码没有办法停下——它会活到应用
重启为止。

## 示例

[margelet_example.marp](margelet_example.marp) —— 和应用里自带的是同一个。
里头就两行，从它开始正好。

## 有问题去哪

[论坛](https://t.me/margeletforum) · [频道](https://t.me/margeletter)
