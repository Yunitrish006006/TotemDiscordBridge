# TotemDiscordBridge

TotemDiscordBridge 將 Minecraft 聊天、玩家動態、管理稽核、公開事件與
伺服器狀態送到外部 Worker，再由 Worker 轉送 Discord Webhook 或 Bot
頻道。

```text
Minecraft Server → TotemDiscordBridge → Worker → Discord
```

目前候選版本為 **0.1.4**，精確搭配 TotemCore **0.4.0**。

## 安裝

Server 放入：

1. Fabric API `0.154.2+26.2`
2. TotemCore `0.4.0`
3. TotemDiscordBridge `0.1.4`

需要遊戲內設定 GUI 的管理員 Client 也必須安裝相同三個 JAR。只用
設定檔與 Server 指令時，一般玩家 Client 不需要 Bridge。

| 項目 | 需求 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3+ |
| Java | 25+ |
| 必要 Totem 模組 | `totem-core =0.4.0` |

Bridge 不要求 Remnant、Automata 或 Nexus。使用 DeadRecall 2.4.7 整合
JAR 時不要再安裝獨立 TotemDiscordBridge。

## 第一次設定

第一次啟動會建立：

```text
config/discord-bridge.json
```

預設內容：

```json
{
  "enabled": false,
  "workerUrl": "",
  "apiKey": "",
  "channels": []
}
```

設定步驟：

1. 部署可接受 Bridge API 的 HTTPS Worker。
2. 在 Worker 設定 `MC_API_KEY`，並配置 Discord Webhook 或 Bot Token。
3. 將 Worker URL 與相同 API Key 填入設定檔。
4. 設定 `enabled` 為 `true`。
5. 執行 `/discordbridge reload`，或重新啟動 Server。

`workerUrl` 結尾不需要 `/`。Bridge 使用：

| Endpoint | 用途 |
| --- | --- |
| `POST /api/mc/chat` | 聊天、玩家與公開事件 |
| `POST /api/mc/server/status` | 開服、關服與健康狀態 |

每個請求都帶 `X-API-Key`；Worker 必須使用與 Server 相同的 secret 驗證。

DeadRecall repository 提供完整的
[Cloudflare Worker 部署說明](https://github.com/Yunitrish006006/DeadRecall/blob/master/docs/discord/worker.md)。

## 遊戲內設定

需要管理員權限：

```text
/discordbridgeui
/discordbridge reload
/discordbridge set <enabled> <workerUrl> <apiKey>
```

`/discordbridgeui` 是 Client command；Server 仍會重新驗證玩家權限才
回傳設定。GUI 不顯示既有 API Key，金鑰欄留空儲存會保留原值。

### 多頻道

```text
/discordbridge channel add <channelId> <name>
/discordbridge channel remove <channelId>
/discordbridge channel list
```

- Channel ID 必須是 17–20 位 Discord snowflake。
- 最多設定 10 個頻道。
- Worker 有 Bot Token 時可送到指定頻道；否則應回退至 Webhook。

## 會轉送的內容

- 玩家聊天、首次加入、加入與離開。
- 玩家死亡、Boss 擊殺、進度、Raid 與村民升級。
- Ban、Pardon、Whitelist、Kick、難度與 Gamerule 管理稽核。
- Server 開啟／關閉、低 TPS 與恢復通知。
- 透過 TotemCore event bus 收到的死亡背包、公開 Space Unit 與功能模組
  管理稽核事件。

Bridge 啟動時會自行註冊 subscriber；各功能模組不存在或沒有發布事件時，
對應通知會安全停用，不需要 DeadRecall 額外接線。

進度翻譯會讀取所有已載入模組的 `en_us`／`zh_tw` 語言資源。原版繁中
語言檔不再內嵌於模組；Server 首次載入時會依目前 Minecraft 版本下載並
驗證 Mojang 官方 `zh_tw` 資產，之後使用版本化快取。

## 安全注意

- 不要提交真實 `apiKey`、Webhook URL 或 Bot Token。
- 不要在 issue、log 或截圖中公開完整設定檔。
- 一般玩家不能讀取 Worker URL、API Key 或頻道列表。
- 管理稽核不轉送完整指令原文。
- 死亡背包與 Space Unit 通知不包含座標或物品內容。
- 設定缺漏或 Worker 失敗時 Bridge 會停用／異步失敗，不應阻塞 Server
  tick。

## 疑難排解

| 問題 | 檢查 |
| --- | --- |
| 啟動後自動停用 | `enabled=true` 時 URL 與 API Key 都不可空白 |
| HTTP 401／403 | Worker 的 `MC_API_KEY` 是否完全相同 |
| 沒有 Discord 訊息 | Worker endpoint、Webhook／Bot Token 與頻道權限 |
| GUI 無法開啟 | Client 是否安裝模組、玩家是否有管理員權限 |
| 部分頻道失敗 | Channel ID 格式、Bot 權限與 10 頻道上限 |

## 開發與建置

```bash
./gradlew build
```

輸出位於 `build/libs/`。Bridge 可在只有 Fabric API 與 TotemCore 的
Dedicated Server 啟動；所有權與相容界線見
[EXTRACTION.md](EXTRACTION.md)。
