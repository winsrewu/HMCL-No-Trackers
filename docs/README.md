<!-- #BEGIN BLOCK -->
<!-- #PROPERTY NAME=TITLE -->
<div align="center">
    <img src="/HMCL/src/main/resources/assets/img/icon@8x.png" alt="HMCL Logo" width="64"/>
</div>

<h1 align="center">Hello Minecraft! Launcher</h1>
<!-- #END BLOCK -->

---

<!-- #BEGIN LANGUAGE_SWITCHER -->
**English** (**Standard**, [uʍoᗡ ǝpᴉsd∩](README_en_Qabs.md)) | 中文 ([简体](README_zh.md), [繁體](README_zh_Hant.md), [文言](README_lzh.md)) | [日本語](README_ja.md) | [español](README_es.md) | [русский](README_ru.md) | [українська](README_uk.md)
<!-- #END LANGUAGE_SWITCHER -->

## Status: Hard Fork

This repository is now a **hard fork** of [HMCL](https://github.com/HMCL-dev/HMCL), not a mirror that follows
upstream commit by commit. The branch no longer merges upstream wholesale: it moves only when we decide to
move it, so the tracking head below is the actual base of everything built here.

### What we track

- **Critical vulnerability fixes.** When upstream — or a library the launcher depends on — ships a fix for a
  security issue, we port it, or implement an equivalent fix ourselves when the upstream approach does not
  fit this fork.
- **Major feature updates.** For example a new mod loader or a new Minecraft version line that cannot
  reasonably be carried as a local patch.

Everything else stays out: minor features, UI work and translation churn from upstream are not imported.

### Upstream tracking head

| Item | Value |
|---|---|
| Upstream commit | [`3ac08d7f6268`](https://github.com/HMCL-dev/HMCL/commit/3ac08d7f62688a9b36326d5e60738bcadb413b88) — *清理 FXUtils (#6706)* |
| Upstream date | 2026-08-19 |
| Upstream version line | 3.17 |
| Current local version | 3.18 |
| Last verified | 2026-11-25 |

Run `git log 3ac08d7f6268..HEAD --oneline` to list the local commits carried on top of this base.

### Security fixes carried locally

| Issue | Handling |
|---|---|
| Java4Shell — CVE-2026-114938 | Ported the upstream mitigation (pure-Java Base64 decoder, `-XX:-UseBASE64Intrinsics`), then added a launcher-side gate: HMCL refuses to start on a vulnerable or unrecognized JVM, and refuses to launch a game while any known Java runtime is affected. |
| FreeType — CVE-2026-50811 / CVE-2026-95512 | Own implementation: before every launch, the FreeType build the instance would load is read from the native library (PE `VERSIONINFO`) and compared against 2.15.0. Instances that render fonts through FreeType also refuse 32-bit runtimes, because no fixed 32-bit build exists. |

## Warning

Convenience is sacrificed for privacy. So network issues may occur, and some features are unavailable.

The following third-party services and features have been removed:

- **Download mirrors** — BMCLAPI (`bmclapi2.bangbang93.com`), Tencent Cloud Maven, `mod.mcimirror.top`, and `alist.8mi.tech`. All downloads now use official Mojang sources only; failures are reported as errors instead of falling back to a mirror.
- **authlib-injector** — the entire third-party Yggdrasil authentication feature (external login, server list, drag-and-drop protocol, and the bundled jar) has been removed.
- **Skin services** — LittleSkin (`littleskin.cn`) and CustomSkinLoader are removed. Offline accounts only support built-in and local-file skins; in-game skin injection is no longer available.
- **Auto-update** — the update channel and download source (`hmcl.huangyuhui.net`) are removed. Updates must be built manually.
- **Help/documentation links** — `docs.hmcl.net` is removed.
- **Terracotta node discovery** — `terracotta.glavo.site` is removed, so the built-in multiplayer feature can no longer discover relay nodes automatically.
- **HMCL-hosted metadata** — `hmcl.glavo.site` is removed, so the Forge, Cleanroom, and legacy FML-libs version lists are now empty. The JSON schema namespace is now the local identifier `hmcl-notrackers/` instead of `schemas.glavo.site`.
- **Forge** - lagacy version listing is unavailable.
- **OptiFine** — version listing is unavailable (it relied solely on BMCLAPI).
- **Microsoft sign-in** — uses the fixed Minecraft Launcher client id (`00000000402b5328`) instead of an officially injected one. After logging in through the browser, copy the full address (starting with `https://login.live.com/oauth20_desktop.srf`) from the address bar and paste it back into the launcher. QR-code (device code) sign-in is not supported by this client id.
- **Translations** — only the English strings (`I18N.properties`) are updated for the new features. Translations in other languages are kept for code stability but are not maintained for the latest changes.

## Download
Build yourself.

## Introduction

HMCL is an open-source, cross-platform Minecraft launcher that supports Mod Management, Game Customizing, ModLoader Installing (Forge, NeoForge, Cleanroom, Fabric, Legacy Fabric, Quilt, LiteLoader, and OptiFine), Modpack Creating, UI Customization, and more.

HMCL has amazing cross-platform capabilities. Not only does it run on different operating systems like Windows, Linux, macOS, and FreeBSD, but it also supports various CPU architectures such as x86, ARM, RISC-V, MIPS, and LoongArch. You can easily enjoy Minecraft across different platforms through HMCL.

For systems and CPU architectures supported by HMCL, please refer to [this table](PLATFORM.md).

## Download

You can download HMCL from the following sources:

- [GitHub Release](https://github.com/HMCL-dev/HMCL/releases)
- [CNB Release](https://cnb.cool/HMCL-dev/HMCL/-/releases)

## Contributing

HMCL is a community-driven open-source project, and everyone is welcome to contribute code or provide suggestions.

You can contribute to HMCL development in the following ways:

- Report bugs or request features by [creating an issue](https://github.com/HMCL-dev/HMCL/issues/new/choose) on GitHub.
- Contribute code by forking the repository on GitHub and [submitting a pull request](https://github.com/HMCL-dev/HMCL/compare).

Before contributing, please read the [Contributing Guide](./Contributing.md), which includes the following:

- [How to build and run HMCL from source](./Contributing.md#build-hmcl)
- [Adjusting HMCL behavior using debug options](./Contributing.md#debug-options)

## Contributors

Since 2015, more than 120 contributors have participated in HMCL. Thank you for your hard work!

[![Contributors](https://contrib.rocks/image?repo=HMCL-dev/HMCL)](https://github.com/HMCL-dev/HMCL/graphs/contributors)

## License

The software is distributed under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html) license with the following additional terms:

### Additional terms under GPLv3 Section 7

1. When you distribute a modified version of the software, you must change the software name or the version number in a reasonable way in order to distinguish it from the original version. (Under [GPLv3, 7(c)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L372-L374))

   The software name and the version number can be edited [here](https://github.com/HMCL-dev/HMCL/blob/86529c6f15b3be54ed8d89ece47566cf57f43eb7/HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java#L36-L38).

2. You must not remove the copyright declaration displayed in the software. (Under [GPLv3, 7(b)](https://github.com/HMCL-dev/HMCL/blob/11820e31a85d8989e41d97476712b07e7094b190/LICENSE#L368-L370))
