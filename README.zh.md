<div align="center">

<img src="assets/banner.png" alt="Margelet" width="640">

**安卓上的 Telegram 分支客户端。**
基于 [DrKLO/Telegram](https://github.com/DrKLO/Telegram)，应用本仓库中的补丁构建。

[English](README.md) · [Русский](README.ru.md)

[![频道](https://img.shields.io/badge/频道-margeletter-8DD1B0?style=flat-square)](https://t.me/margeletter)
[![论坛](https://img.shields.io/badge/论坛-margeletforum-8DD1B0?style=flat-square)](https://t.me/margeletforum)
[![许可证](https://img.shields.io/badge/许可证-GPL--2.0--or--later-8DD1B0?style=flat-square)](#许可证)

</div>

> 这份中文说明由 Claude（本项目的开发助手）翻译，未经母语者校对。发现错误请到论坛指出。

---

包名 `cat.narezany.margelet`，与官方 Telegram **并存**安装，不会覆盖。仅支持 arm64。

分支添加的功能都在一处：**设置 → Margelet**，位于第一行。

## 新增功能

<details>
<summary><b>输入框可以按需增高</b></summary>

官方 Telegram 的输入框到六行就停止增高，改为滚动。这里由你决定：2 到 15 行，或者
「最大」——只要屏幕还有空间就继续增高。框内文字大小也可以调整。

单条消息 4096 字的限制仍然存在。那是服务器的限制，不属于客户端，任何客户端都无法解除。
</details>

<details>
<summary><b>输入框可以移到聊天顶部</b></summary>

输入框移到聊天标题栏下方，消息列表为它预留的空间也一并移到顶部。键盘和附件面板仍在底部
——它们属于屏幕，而不属于输入框。

该设置在下一次打开聊天时生效：已打开的聊天中，一半尺寸已按原来的位置算好。
</details>

<details>
<summary><b>音乐标签：标题、艺术家、封面</b></summary>

长按聊天中的音乐，选择「音轨标签」。在官方 Telegram 中这要通过机器人完成，也就是为了三行
文字把自己的文件交给别人的服务器。这里全部在手机上完成：标签写入文件副本，副本发回同一个聊天。

封面用 Telegram 自带的图库选择，并用它自己的头像裁剪界面裁剪。
</details>

<details>
<summary><b>主播模式</b></summary>

应用显示手机号的所有位置都会用圆点遮住。可选择同时遮住他人的号码和自己的用户名。

刻意没有做「点击显示」：直播时误触屏幕，正是开启这个模式所要防的情况。只能通过设置里的开关打开。
</details>

<details>
<summary><b>资料页显示 ID</b></summary>

用户、机器人、群组和频道的资料页会出现一行数字 ID，点击即可复制。可以关闭。
</details>

<details>
<summary><b>已下架的礼物</b></summary>

Telegram 从目录中移除的礼物会被重新加回列表末尾，可以再次赠送。购买仍由服务器确认：如果
礼物确实已关闭，发送只会失败。

这个做法和礼物清单来自 **[@binbash_0](https://t.me/binbash_0)**，即 *Deleted Gift Sender*
插件的作者。这里的代码是重新编写的，思路是他的。
</details>

<details>
<summary><b>开箱即用的深绿主题</b></summary>

首次启动时启用 Telegram 自带的 *Night* 主题及其绿色强调色。只做一次；之后你换成别的主题，
分支不再干预。
</details>

<details>
<summary><b>六种颜色的自有图标</b></summary>

绿色、夜色、薰衣草、沙色、海色、玫瑰色——全部出现在应用图标选择列表中，没有一个需要会员。
</details>

<details>
<summary><b>捐赠</b></summary>

分支菜单里单独的一行：作者的收款信息，点一下即复制。

应用内没有付款按钮，以后也不会有——通讯软件的分支不是输入支付信息的地方。捐赠也
不解锁任何东西：这里没有付费功能。
</details>

<details>
<summary><b>名字旁的徽章</b></summary>

分支的作者和他最好的朋友，名字旁边各有一枚徽章——资料页、资料下方单独的一块，以及
聊天列表里都能看到。点开会看到图标在窗口里做三维旋转，分别是绿色和薰衣草色。

Margelet 的频道和论坛也各有一枚。那里的徽章回答的是「这是不是那个频道」：编号写死在
构建里。

徽章不证明任何事，也不向服务器询问：谁构建自己的分支，就把自己的人写进去。窗口里
就是这么写的。
</details>

<details>
<summary><b>发作</b></summary>

应用里的所有文字都会不停变换颜色。毫无用处。

这不是闪烁：色相平滑地循环，亮度保持不变。每秒三到三十次的闪烁正是诱发光敏性癫痫
的原因，这种东西即使有人要求也不能做。开启前仍然会有一次提醒。
</details>

<details>
<summary><b>一只猫</b></summary>

在某处。想让自己的猫进入应用，请联系 [@narezany](https://t.me/narezany)。
</details>

## 自行构建

需要 SDK 35 与 build-tools 35.0.0、NDK 27.2.12479018、JDK 21。

```bash
git clone https://github.com/DrKLO/Telegram
cd Telegram
git checkout $(cat ../margelet/patch/UPSTREAM)
git submodule update --init --recursive
git apply ../margelet/patch/margelet.patch
# 把你自己的 api_id / api_hash（来自 https://my.telegram.org）填入
# TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java
gradle :TMessagesProj_AppStandalone:assembleAfatStandalone
```

生成的 apk 在 `TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/`。

补丁同时包含改动和新增文件，直接应用即可。曾经并非如此：`git diff` 会默默略过 git 从未
记录过的文件，最早发布的补丁因此少了分支的全部新类。所以 `java/` 和 `res/` 也以普通副本
保留在这里，补丁现在则在 `git add -N` 之后生成。

## 本仓库不包含

- **api_id / api_hash。** 构建者的私人密钥。请到 my.telegram.org 申请自己的，绝不提交到这里。
- **google-services.json。** Telegram 源码中自带的那一份描述的是他们自己的 Firebase 项目，
  其中没有我们的包名。在分支拥有自己的项目之前该插件保持关闭，也就是说
  **应用未运行时收不到推送通知。**

## 目录说明

| | |
|---|---|
| `patch/margelet.patch` | 对 Telegram 源码的全部改动，合为一个文件 |
| `patch/UPSTREAM` | 补丁所对应的 Telegram 提交 |
| `FEATURES.md` | **每项改动的位置与原因**——移植时要读的文件 |
| `java/`、`res/` | 分支整体新增的文件 |
| `tools/` | 脚本：绘制图标、写入资源、合成声音 |
| `assets/` | 徽标，svg 与 png |
| `ATTRIBUTION.md` | 其中并非我们所作的部分 |

把分支移植到新版 Telegram 之前，先读 `FEATURES.md`。Telegram 经常重写自己的界面，补丁迟早
会无法应用。每条记录都说明做了什么、在哪里，以及——重写之后仍然有效的那部分——为什么这么做。

## 许可证

Telegram 源码采用 GPL v2 或更新版本，分支继承该许可证。如果你把 apk 交给别人，就有义务同时
提供构建它所用的源码：本仓库加上 `patch/UPSTREAM` 中记录的提交，正是这份源码。
