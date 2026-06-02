# ProximityVC セットアップガイド

距離に応じてDiscordのVC音量を自動調整するMinecraftクライアントMod「ProximityVC」のセットアップ手順です。

---

## 前提条件

参加者全員が以下をそれぞれ自分のPCに用意してください。

- Minecraft Java Edition（正規アカウント）
- [NeoForge 21.1.228](https://neoforged.net/) インストール済み
- [Node.js v18.20.7 以上](https://nodejs.org/)
- Discord デスクトップアプリ（起動中であること）
- Discord 同一VCチャンネルに全員参加済み

---

## ステップ 1 — Discord Developerアプリを作成する（全員が各自で実施）

> **重要:** 1人1アプリ。他人のアプリを共有しないでください。

1. [Discord Developer Portal](https://discord.com/developers/applications) にアクセスしてログイン
2. 「**New Application**」でアプリを作成（名前例: `ProximityVC`）
3. 左メニュー「**OAuth2**」を開き、**Client ID** と **Client Secret** をコピーして手元に保存
4. 「**Redirects**」に `http://localhost` を追加して保存

---

## ステップ 2 — ブリッジ（Node.js）をセットアップする

### 2-1. ファイルを配置する

`bridge/` フォルダをそのままPCの任意の場所に置きます。

```
bridge/
├── index.js
├── discordRpc.js
├── package.json
├── config.json.example   ← これをコピーして config.json を作る
├── start.bat             (Windows)
└── start.sh              (Linux / macOS)
```

### 2-2. 依存パッケージをインストールする

`bridge/` フォルダをターミナルで開いて実行：

```bash
npm install
```

### 2-3. config.json を作成する

`config.json.example` をコピーして `config.json` にリネームし、ステップ 1 で控えた値を入力します。

```json
{
  "clientId": "ここにClient IDを貼り付け",
  "clientSecret": "ここにClient Secretを貼り付け",
  "port": 7878
}
```

> **注意:** `config.json` には Client Secret が含まれます。他人と共有したり Git にコミットしないでください。

---

## ステップ 3 — Modをインストールする

### バニラランチャー / NeoForge インストーラーを使う場合

1. リリースページから `proximitychat-*.jar` をダウンロード
2. `.minecraft/mods/` フォルダに配置
3. NeoForge プロファイルでMinecraftを起動

### Modrinth App を使う場合

1. リリースページから `proximitychat-*.jar` をダウンロード
2. Modrinth App でプロファイルを選択し「**Mods**」→「**Add from file**」から `.jar` を追加
   または、プロファイルの `mods/` フォルダに直接配置
3. そのプロファイルでMinecraftを起動

---

## ステップ 4 — 設定ファイルを配置する

Minecraftを一度起動すると、設定ファイルが**起動したプロファイルのゲームディレクトリ**に自動生成されます。

### バニラランチャー

**Windows:**
```
%APPDATA%\.minecraft\config\proximitychat\
├── proximity_config.json       ← Mod動作設定
└── minecraft_discord_map.json  ← UUID↔Discord IDマッピング
```

**Linux / macOS:**
```
~/.minecraft/config/proximitychat/
├── proximity_config.json
└── minecraft_discord_map.json
```

### Modrinth App（プロファイルごとに独立）

**Windows:**
```
%APPDATA%\ModrinthApp\profiles\<プロファイル名>\config\proximitychat\
├── proximity_config.json
└── minecraft_discord_map.json
```

**Linux:**
```
~/.local/share/com.modrinth.theseus/profiles/<プロファイル名>/config/proximitychat/
```

> **補足:** Modrinth では各プロファイルがゲームディレクトリを独立して持つため、
> 設定ファイルもプロファイルごとに別管理されます。複数のModpackを使い分けている場合は、
> それぞれのプロファイルに設定ファイルを配置してください。

### 4-1. minecraft_discord_map.json を編集する（全員で同じ内容）

MinecraftのUUIDとDiscordユーザーIDを対応づけます。**全員が同じ内容のファイルを使います。**

```json
{
  "version": 1,
  "mappings": {
    "Minecraft UUID（ハイフン付き）": "Discord ユーザーID（数字）",
    "550e8400-e29b-41d4-a716-446655440000": "123456789012345678",
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8": "987654321098765432"
  }
}
```

#### Minecraft UUID の調べ方
- [https://mcuuid.net/](https://mcuuid.net/) でプレイヤー名を入力

#### Discord ユーザーID の調べ方
1. Discordの設定 →「詳細設定」→「開発者モード」をON
2. DiscordでユーザーIDを知りたい人を右クリック →「ユーザーIDをコピー」

### 4-2. proximity_config.json を確認する（任意）

デフォルト設定で動作しますが、必要に応じて調整してください。

```json
{
  "bridgePort": 7878,
  "maxDistance": 32.0,
  "minDistance": 2.0,
  "falloffType": "LINEAR",
  "updateIntervalTicks": 10,
  "maxVolume": 200,
  "minVolume": 0
}
```

| フィールド | 説明 | デフォルト |
|---|---|---|
| `maxDistance` | この距離（ブロック）以上は音量0 | `32.0` |
| `minDistance` | この距離以下は最大音量 | `2.0` |
| `falloffType` | 減衰式（`LINEAR` / `QUADRATIC` / `STEPPED`） | `LINEAR` |
| `maxVolume` | Discordの最大音量（0〜200） | `200` |
| `minVolume` | Discordの最小音量（0〜200） | `0` |
| `updateIntervalTicks` | 音量更新間隔（tick、20 tick = 1秒） | `10` |

---

## ステップ 5 — 起動手順（毎回）

**以下の順番で起動してください。**

1. **Discord** を起動してVCに参加
2. **ブリッジを起動**
   - Windows: `bridge/start.bat` をダブルクリック
   - Linux / macOS: ターミナルで `bash bridge/start.sh`
   - 初回のみ Discord に認証ダイアログが表示されるので「承認」をクリック
3. **Minecraft** を起動してサーバーに参加

**終了時は必ずMinecraftを先に終了してからブリッジを終了してください。**  
（逆にするとDiscordの音量が元に戻りません）

---

## チャットコマンド

ゲーム内チャットで使えるコマンドです。

| コマンド | 説明 |
|---|---|
| `/proximitychat status` | ブリッジ接続状態・マッピング件数を確認 |
| `/proximitychat reload` | `proximity_config.json` と `minecraft_discord_map.json` を再読み込み |
| `/proximitychat debug` | 近くにいるプレイヤーの距離・音量をチャットに表示 |

---

## トラブルシューティング

### ブリッジが「not reachable」と表示される
- `start.bat` / `start.sh` でブリッジが起動しているか確認
- `config.json` の `port` と `proximity_config.json` の `bridgePort` が一致しているか確認

### 初回起動時にDiscordの認証ダイアログが出ない
- Discordが起動していることを確認
- `config.json` の `clientId` が正しいか確認
- `bridge/token.json` が存在する場合は削除して再起動

### 音量が変化しない
- `/proximitychat status` で接続状態を確認
- `minecraft_discord_map.json` に自分と相手のマッピングが正しく記載されているか確認
- `/proximitychat debug` で距離・音量の計算値を確認

### token.json を削除して再認証したい
`bridge/token.json` を削除してブリッジを再起動すると、再度認証ダイアログが表示されます。
