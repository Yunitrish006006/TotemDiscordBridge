# 🎮 DeadRecall 模組完整文檔

## 📖 項目概述

**DeadRecall** 是一個 Minecraft Fabric 模組，提供多等級背包系統、死亡物品保護、銅魁儡綁定分類、Discord 聊天橋接及煉金配方等功能。

### 📊 技術資訊
- **Minecraft 版本**：26.2
- **模組載入器**：Fabric（需 Fabric API）
- **當前版本**：v1.7.2
- **授權**：BSD-3-Clause

---

## 🧑‍🎮 生存會體驗到的功能

### 💬 Discord 聊天橋接

玩家在遊戲中發送的聊天訊息，會**自動轉發到 Discord 頻道**；伺服器開啟與關閉時，也會自動提示狀態，讓不在線的玩家也能掌握伺服器動態。

#### 運作方式
- 遊戲內聊天 → Cloudflare Worker API → Discord Webhook → Discord 頻道
- 伺服器開啟 / 關閉 → Cloudflare Worker API → Discord Webhook → Discord 頻道
- 聊天格式：`**玩家名稱**: 訊息內容`
- 狀態格式：`**伺服器已開啟**` 或 `**伺服器已關閉**`，並附上玩家數、版本與 TPS
- 使用非同步傳送，不影響遊戲效能

#### 設定方式（伺服器管理員）
1. 在伺服器 `config/` 目錄找到 `discord-bridge.json`（首次啟動自動建立）
2. 編輯設定檔，填入下列欄位：
```json
{
  "enabled": true,
  "workerUrl": "https://your-worker.workers.dev",
  "apiKey": "your-api-key"
}
```
| 欄位 | 要填寫什麼 | 範例 |
|------|------------|------|
| `enabled` | 是否啟用 Discord 聊天橋接 | `true` |
| `workerUrl` | 你的 Cloudflare Worker 網址 | `https://your-worker.workers.dev` |
| `apiKey` | 與 Worker 端一致的 API 金鑰 | `your-api-key` |
3. 重啟伺服器後生效

#### Cloudflare Worker 設定（管理員）
將 `cloudflare-worker-example.js` 部署到 Cloudflare Worker，並設定以下環境變數：
| 環境變數 | 要填寫什麼 | 範例 |
|----------|------------|------|
| `MC_API_KEY` | 與 `discord-bridge.json` 中 `apiKey` 相同的金鑰 | `your-api-key` |
| `DISCORD_WEBHOOK_URLS` | Discord Webhook URL 的 JSON 陣列 | `["https://discord.com/api/webhooks/xxx/yyy"]` |

`DISCORD_WEBHOOK_URLS` 範例：
  ```
  ["https://discord.com/api/webhooks/xxx/yyy"]
  ```

> **注意**：若未啟用或設定不完整，此功能會自動停用，不影響其他功能正常運作。

---

### 💀 死亡背包系統

玩家死亡後，一般物品會自動收集進一個特殊的「死亡背包」，並生成在死亡地點。玩家身上的模組背包不會被收進死亡背包，會保留成獨立掉落物。

#### 生存體驗流程
1. **死亡**：玩家因任何原因死亡
2. **自動收集**：模組掃描死亡地點附近 **10 格範圍**內的所有掉落物，但跳過背包類物品
3. **生成背包**：死亡背包（物品實體）出現在死亡座標
4. **收到通知**：聊天欄顯示 `§e你的物品已被收集到死亡背包中！`
5. **取回物品**：返回死亡地點，等待 **2 秒拾取延遲**後拾起背包，右鍵打開取回物品

#### 死亡背包特性
| 屬性 | 說明 |
|------|------|
| 容量 | 動態（依物品數量，最多 6 排 54 格） |
| 搜尋範圍 | 10 格立方體 |
| 拾取延遲 | 2 秒 |
| 防火 | ✅ 背包本身防火，物品不會被燒毀 |
| 存活時間 | 永久（不會自然消失） |
| Tooltip | 顯示物品數量與排數 |
| 背包處理 | 模組背包與死亡背包不會被收進死亡背包，會作為獨立物品掉落 |

> ⚠️ 若死亡時物品欄為空，或附近沒有掉落物，則不會生成死亡背包。

---

### 🎒 背包系統

模組提供 **4 個等級**的可升級背包，讓玩家在生存中逐步擴充攜帶空間。

#### 背包等級總覽

| 等級 | 物品 ID | 容量 | 防火 | 取得方式 | 模板 | 基底 | 附加材料 |
|------|---------|------|------|---------|------|------|----------|
| 🟤 **基礎背包** | `backpack_basic` | 9格（1排） | ❌ | 鍛造台升級 | 束口袋 | 束口袋 | 皮革 |
| 🟦 **標準背包** | `backpack_standard` | 18格（2排） | ❌ | 鍛造台升級 | 束口袋 | 基礎背包 | 鐵錠 |
| ⚫ **進階背包** | `backpack_advanced` | 27格（3排） | ❌ | 鍛造台升級 | 束口袋 | 標準背包 | 鑽石 |
| 🟣 **獄髓背包** | `backpack_netherite` | 36格（4排） | ✅ | 鍛造台升級 | 獄髓升級模板 | 進階背包 | 獄髓錠 |

#### 背包特性
- ✅ 右鍵使用開啟背包界面
- ✅ 物品保存（關閉後不丟失）
- ✅ Tooltip 顯示等級與格數
- ✅ 每次只能堆疊 1 個（不可疊放）
- ✅ Shift+點擊快速移動物品

#### 背包物品被破壞時
- 背包物品實體若因爆炸、仙人掌或自然時間到而被移除，背包內物品會掉出來
- 有傷害來源位置時，內容物會朝傷害來源的反方向噴出
- 仙人掌傷害若沒有提供來源座標，會改用附近仙人掌方塊中心當來源
- 自然時間到沒有傷害來源，會依背包物品當下速度反向；若沒有速度則水平隨機散開

#### 整理快捷鍵
- 預設按鍵：中鍵
- 會整理你目前開著的物品介面內容，將相同物品盡量堆疊並依物品排序
- 整理玩家背包時只處理主背包與快捷列，不會移動盔甲、盾牌/副手、身體裝備或鞍等裝備欄位
- 可到 **設定 → 按鍵綁定 → DeadRecall** 重新指定快捷鍵

---

### 🤖 銅魁儡綁定分類（銅板手）

銅板手可以讓指定的銅魁儡記住多個目標容器。銅魁儡仍然會從銅箱子拿物品，但放置目標會改成玩家綁定的容器，用來做簡單的自動分類。

#### 新增物品
| 物品 | 物品 ID | 說明 |
|------|---------|------|
| 銅板手 | `deadrecall:copper_wrench` | 用來選取銅魁儡並綁定分類容器 |

> 目前尚未加入生存合成配方，測試時可用 `/give @s deadrecall:copper_wrench` 取得。

#### 使用方法
1. 手持銅板手，左鍵點擊一隻銅魁儡來選取目標魁儡
2. 系統提示已選取銅魁儡後，右鍵點擊要綁定的容器
3. 綁定成功後，該容器會被加入這隻銅魁儡的綁定清單
4. 選取的銅魁儡會保留在銅板手資料中，可連續右鍵多個容器加入同一隻銅魁儡
5. 若要解除單一目標容器，先選取該銅魁儡，再左鍵點擊要解除的已綁定容器
6. Shift+右鍵銅魁儡會開啟自訂選項 UI，顯示已綁定容器的 icon、類型、維度與座標
7. Shift+左鍵銅魁儡會顯示粒子路徑，指向目前同維度且可用的所有綁定容器
8. 綁定清單 UI 右上角可以切換這隻銅魁儡的「運作 / 停止」狀態

#### 綁定規則
| 行為 | 結果 |
|------|------|
| 沒有綁定容器的銅魁儡 | 不會搬運銅箱子內的物品 |
| 左鍵銅魁儡 | 選取這隻銅魁儡，並避免造成攻擊傷害 |
| 右鍵容器且銅板手已有選取魁儡 | 把該容器加入選取魁儡的綁定清單 |
| 右鍵容器但銅板手沒有選取魁儡 | 不綁定，保留原方塊互動 |
| 左鍵容器且銅板手已有選取魁儡 | 若該容器在選取魁儡的綁定清單中，移除該容器 |
| 左鍵容器但銅板手沒有選取魁儡 | 顯示先選取銅魁儡的提示，避免誤破壞容器 |
| 右鍵銅箱子系列（`BlockTags.COPPER_CHESTS`） | 不允許綁定；銅箱子系列只作為來源容器 |
| Shift+右鍵銅魁儡 | 開啟自訂選項 UI，顯示綁定容器 icon、類型、維度與座標 |
| Shift+左鍵已綁定的銅魁儡 | 顯示所有同維度且可用綁定容器的粒子路徑 |
| UI 右上角按下「停止」 | 該銅魁儡停止搬運；已有綁定仍保留 |
| UI 右上角按下「運作」 | 該銅魁儡恢復搬運 |
| 右鍵非容器方塊 | 顯示錯誤提示，不會綁定 |
| 重複綁定同一個容器 | 顯示已綁定提示，不會重複加入 |
| 已綁定的目標方塊被破壞或不再是容器 | 自動從該銅魁儡的綁定清單移除 |
| 綁定容器在其他維度 | 不會被當成本次搬運目標 |

> 綁定清單 UI 是 client 自訂畫面，不使用玩家物品欄或原版箱子介面；清單過長時可滾動。
> 失效綁定只會在目標座標所在 chunk 已載入時判定；chunk 未載入時會先保留，避免誤刪。

#### 搬運與分類邏輯
- 銅魁儡至少要綁定 1 個容器才會開始搬運物品
- 銅魁儡必須處於「運作中」才會啟動搬運；「停止」狀態會保留綁定但不搬運
- 銅魁儡的**來源**仍是原版銅箱子（`BlockTags.COPPER_CHESTS`）
- 銅魁儡的**目的地**會改成銅板手綁定的容器清單
- 銅箱子系列只作為來源，不允許綁定成目的地；舊資料若已綁定銅箱子，會被自動清理
- 取物時會從來源銅箱子的 slot 0 開始往後掃，拿第一個非空格的物品
- 每次最多從來源銅箱子拿出 16 個物品，沿用原版銅魁儡搬運量
- 銅魁儡拿起物品後，會依照綁定順序逐一檢查容器，不再用最近距離直接決定
- 綁定容器必須已經有相同物品與相同 Data Components，才算這個物品的分類目標
- 若已有相同物品但該堆疊已滿，仍可放入同一容器的空格
- 單純空箱或只有空格的容器不算分類命中，避免最近空箱吃掉所有物品
- 取物前會先掃描整個來源銅箱子；只要還有任一物品能放進任一綁定容器，就繼續搬運

#### 避免重複拿同一個不能分類物品
若銅魁儡拿起物品後，某個綁定容器找不到可用位置：
1. 模組會記住來源銅箱子位置與來源槽位
2. 模組會記錄這個物品已嘗試過該綁定容器
3. 銅魁儡會繼續找下一個尚未嘗試的綁定容器
4. 直到同維度的綁定容器都試過，仍找不到分類位置，才把物品放回原來源銅箱子後段
5. 放回時會由後往前找可堆疊或空槽
6. 如果來源銅箱子後段沒有空間，會把原槽位後方的物品往前挪，並把不能分類的物品放到最後面
7. 放回成功後清除手上物品與嘗試紀錄，讓銅魁儡繼續尋找下一個可分類物品

#### 全部物品都無法分類時
若來源銅箱子裡有物品，但整箱掃描後沒有任何物品能放進目前同維度的綁定容器：
1. 銅魁儡不會再拿起同一批無法分類的物品反覆嘗試
2. 模組會在銅魁儡身上記錄阻塞狀態與三個快照：來源銅箱子內容、綁定容器清單、綁定目標容器內容
3. 銅魁儡會停止搬運 AI，留在原地週期性上下跳，表示目前沒有可分類目標
4. 只要來源銅箱子內容、綁定清單，或綁定目標容器內容有更新，阻塞狀態會自動解除
5. 解除後銅魁儡會重新檢查來源銅箱子，若仍然全都無法分類，會再次進入原地跳躍狀態

#### 資料儲存
| 資料 | 儲存位置 | 說明 |
|------|----------|------|
| 暫時選取的銅魁儡 UUID | 銅板手物品 `CUSTOM_DATA` | 點銅魁儡後、點容器前暫存 |
| 綁定容器清單 | 銅魁儡實體 `CUSTOM_DATA` | 每筆包含維度與 `x/y/z` 座標 |
| 本次搬運來源 | 銅魁儡實體 `CUSTOM_DATA` | 記錄來源銅箱子與來源槽位，失敗回放用 |
| 本次已嘗試目的地 | 銅魁儡實體 `CUSTOM_DATA` | 同一個手上物品已試過哪些綁定容器 |
| 全箱無法分類阻塞狀態 | 銅魁儡實體 `CUSTOM_DATA` | 記錄來源、綁定與目標容器快照，等待資料更新後解除 |

> 綁定容器數量目前沒有硬性上限；資料以清單存在銅魁儡身上。實際使用仍建議保持合理數量，避免路徑顯示與每次搜尋容器時成本過高。

---

### ⚗️ 煉金配方（新增資源）

模組加入豬糞、木灰與煉藥鍋煉製流程，作為硝石與火藥的生存製作路線。

#### 新增物品
| 物品 | 物品 ID | 說明 |
|------|---------|------|
| 硫磺 | `sulfur` | Minecraft 原版資源 |
| 硝石 | `saltpeter` | 由煉藥鍋煉製取得，作為火藥材料 |
| 豬糞 | `pig_manure` | 用鏟子右鍵帶豬糞的泥土系列方塊取得 |
| 木灰 | `wood_ash` | 乾草捆放入熔爐燒製取得 |
| 缽 | `stone_bowl` | 對硫磺方塊使用後可裝填硫磺 |
| 帶硫磺的缽 | `sulfur_bowl` | 作為煉金火藥材料，合成後回傳缽 |

#### 豬糞取得
- 豬會加入類似羊吃草的 AI，成年豬約每 1000 tick 嘗試一次，幼年豬約每 50 tick 嘗試一次
- 豬吃草觸發時，腳下的泥土系列方塊會變成「帶豬糞」版本
- 目前支援：泥土、草地、砂土、扎根土、灰壤、菌絲土、泥巴
- 手持任意鏟子右鍵帶豬糞方塊，會取得 1 個豬糞，方塊回復成原本的泥土系列方塊

#### 木灰取得
```
乾草捆 + 熔爐 => 木灰
```

#### 硝石煉製
1. 在煉藥鍋正下方放一個已點燃的營火或靈魂營火
2. 把煉藥鍋裝滿水（3 層水）
3. 將木灰、任一種蘑菇、豬糞各 1 個丟進滿水煉藥鍋，或手持材料右鍵投入
4. 第一個材料投入後，滿水煉藥鍋會變成「煉製中的煉藥鍋」，並在方塊實體內記錄已投入與已煮入的內容
5. 鍋子會在營火上慢慢煮，每完成一份材料就消耗 1 層水
6. 三種材料都煮完後，煉藥鍋變回空鍋，並在鍋內生成 1 個硝石

> 硝石工作台配方已移除；硝石改由上述煉藥鍋流程取得。

#### 火藥配方

**裝填硫磺**（對方塊互動）：
```
手持缽，右鍵點擊硫磺方塊
=> 變為「帶硫磺的缽」，硫磺方塊會被消耗
```

**缽**（工作台）：
```
[石] [空] [石]
[空] [石] [空]
[空] [燧石] [空]
```

**火藥 × 4**（工作台，需帶硫磺的缽）：
```
帶硫磺的缽 + 硝石 + 木炭/煤（無序合成）
```

> 舊的「煉金法 2」（原硫磺 + 硝石 + 木炭的工作台配方）已取消。

---

### 📜 指令

| 指令 | 權限 | 說明 |
|------|------|------|
| `/back` | 一般玩家 | 傳送回上次死亡地點（使用一次後失效） |
| `/give @s deadrecall:backpack_basic` | OP | 給予基礎背包 |
| `/give @s deadrecall:backpack_standard` | OP | 給予標準背包 |
| `/give @s deadrecall:backpack_advanced` | OP | 給予進階背包 |
| `/give @s deadrecall:backpack_netherite` | OP | 給予獄髓背包 |
| `/give @s deadrecall:death_backpack` | OP | 給予死亡背包（測試用） |
| `/give @s minecraft:sulfur` | OP | 給予硫磺（原版） |
| `/give @s deadrecall:saltpeter` | OP | 給予硝石 |
| `/give @s deadrecall:pig_manure` | OP | 給予豬糞 |
| `/give @s deadrecall:wood_ash` | OP | 給予木灰 |
| `/give @s deadrecall:stone_bowl` | OP | 給予缽 |
| `/give @s deadrecall:sulfur_bowl` | OP | 給予帶硫磺的缽 |
| `/give @s deadrecall:copper_wrench` | OP | 給予銅板手（銅魁儡綁定分類用） |

#### `/back` 指令說明
- 玩家死亡後，死亡座標會被**自動記錄**
- 執行 `/back` 後立即傳送至死亡地點
- **傳送後座標清除**，同一次死亡只能使用一次
- 若無死亡座標，顯示 `§c沒有死亡座標可傳送！`

---

## 📈 更新日誌

### v1.7.2（當前版本）
- ✅ 新增背包整理快捷鍵（預設中鍵）
- ✅ 新增銅板手，可讓一隻銅魁儡綁定多個分類容器
- ✅ 新增銅魁儡分類邏輯：從銅箱子取物，放入綁定容器，找不到目標時跳過並回放
- ✅ 新增 Discord 聊天橋接功能（Cloudflare Worker 架構）
- ✅ 新增伺服器開啟與關閉 Discord 狀態提示
- ✅ 新增 `/back` 死亡座標傳送指令
- ✅ 新增硝石、豬糞、木灰、缽、帶硫磺的缽物品（硫磺改用原版）
- ✅ 新增豬吃草生成帶豬糞泥土系列方塊，鏟子右鍵可取得豬糞
- ✅ 新增乾草捆熔爐燒製木灰
- ✅ 新增營火滿水煉藥鍋煉製硝石流程
- ✅ 新增缽右鍵硫磺方塊裝填機制（會消耗方塊）
- ✅ 新增煉金火藥配方，取消原硫磺版煉金法 2
- ✅ 死亡背包改為動態容量（依物品數最多 54 格）
- ✅ 死亡背包永久不消失（`setUnlimitedLifetime`）
- ✅ 修復 IndexOutOfBoundsException 網路同步問題

### v1.5.0
- ✅ 實現多等級背包（基礎／標準／進階／獄髓）
- ✅ 添加鍛造台升級機制

### v1.4.1
- ✅ 解決物品放入背包後丟失的問題
- ✅ 完善 Data Components API 儲存實作

### v1.3.0
- ✅ 修復背包界面顯示問題
- ✅ 優化槽位佈局

### v1.2.0
- ✅ 動態界面大小（依等級調整排數）

### v1.1.0
- ✅ 修復物品無法保存的問題
- ✅ 改用 Player+Hand 引用系統

### v1.0.0
- ✅ 基本背包功能
- ✅ 物品保存機制

---

## 🛠️ 開發提醒

### 項目結構
```
DeadRecall/
├── src/main/java/com/adaptor/deadrecall/
│   ├── Deadrecall.java                 # 主入口：物品註冊、事件監聽、指令
│   ├── DiscordBridge.java              # Discord 橋接（Cloudflare Worker HTTP）
│   ├── DeathLocationManager.java       # 死亡座標管理（UUID → BlockPos）
│   ├── alchemy/
│   │   └── AlchemyHandler.java        # 豬糞採集、煉藥鍋投料與掉落物投入處理
│   ├── block/
│   │   ├── ModBlocks.java             # 糞便地面方塊與煉製中煉藥鍋註冊
│   │   ├── PigManureBlock.java        # 帶豬糞泥土系列方塊
│   │   ├── AlchemyCauldronBlock.java  # 帶 BlockEntity 的煉製中煉藥鍋
│   │   └── entity/
│   │       ├── ModBlockEntities.java
│   │       └── AlchemyCauldronBlockEntity.java
│   ├── entity/
│   │   └── ai/
│   │       └── PigManureGoal.java     # 豬吃草並產生帶豬糞方塊的 AI
│   ├── item/
│   │   ├── ModItems.java              # 所有物品的靜態常量與註冊
│   │   ├── TieredBackpackItem.java    # 等級背包（含 BackpackTier enum）
│   │   ├── DeathBackpackItem.java     # 死亡背包（動態容量）
│   │   ├── BackpackItemHelper.java    # 背包物品判定與內容掉落輔助
│   │   └── copper/
│   │       ├── CopperWrenchItem.java          # 銅板手物品本體
│   │       └── CopperGolemWrenchHandler.java  # 銅魁儡綁定、粒子與分類輔助邏輯
│   ├── mixin/
│   │   ├── ItemEntityMixin.java       # 背包物品實體被破壞或自然消失時掉出內容物
│   │   ├── PigMixin.java              # 將 PigManureGoal 加入豬的目標選擇器
│   │   └── TransportItemsBetweenContainersMixin.java # 攔截銅魁儡搬運行為
│   └── inventory/
│       └── BackpackInventory.java     # 背包物品欄邏輯（Data Components 儲存）
├── src/main/resources/data/deadrecall/recipe/
│   ├── backpack.json                  # 基礎背包鍛造台配方
│   ├── backpack_standard_smithing.json
│   ├── backpack_advanced_smithing.json
│   ├── backpack_netherite_smithing.json
│   ├── wood_ash_from_hay_block_smelting.json
│   ├── gunpowder_from_alchemy.json
│   └── stone_bowl.json
├── src/main/resources/assets/deadrecall/lang/
│   ├── zh_tw.json
│   ├── zh_cn.json
│   └── en_us.json
├── cloudflare-worker-example.js        # Discord Worker 範例程式碼
├── discord-bridge.json                 # （執行時於 config/ 自動產生）
└── fabric.mod.json
```

### 關鍵技術注意事項

#### Discord Bridge
- 聊天訊息與伺服器啟動通知使用單執行緒 `ExecutorService` 非同步傳送，避免阻塞主線程
- 伺服器關閉通知在停止流程中同步送出，降低伺服器退出太快導致訊息未送出的機率
- 設定檔路徑：`<server>/config/discord-bridge.json`
- API 端點：`POST {workerUrl}/api/mc/chat`、`POST {workerUrl}/api/mc/server/status`，Header：`X-API-Key`
- 伺服器啟動時透過 `ServerLifecycleEvents.SERVER_STARTED` 回報 `伺服器已開啟`
- 伺服器停止流程開始時透過 `ServerLifecycleEvents.SERVER_STOPPING` 回報 `伺服器已關閉`

#### 死亡背包
- 使用 `ServerLivingEntityEvents.AFTER_DEATH` 監聽，雙層 `execute()` 確保在物品掉落後才收集
- 收集範圍：`AABB.inflate(10.0)`，收集所有實體（非僅特定玩家的）
- 收集時會跳過 `TieredBackpackItem` 與 `DeathBackpackItem`，讓玩家身上的背包保持為獨立掉落物
- 使用 `DataComponents.CONTAINER` + `ItemContainerContents` 儲存物品
- `setUnlimitedLifetime()` 防止背包自然消失
- `setPickUpDelay(40)` = 2 秒（20 ticks/秒）

#### `/back` 指令
- `DeathLocationManager` 以 `HashMap<UUID, DeathLocation>` 暫存死亡座標
- 使用後呼叫 `clearDeathLocation()` 清除，避免重複傳送
- 注意：目前不處理跨維度死亡傳送（`worldRegistryKey` 已儲存但未使用）

#### 背包儲存
- 使用原版 `DataComponents.CONTAINER`（`ItemContainerContents`）儲存物品，相容性高
- `BackpackInventory` 在開啟時從 DataComponent 讀取，關閉時寫回
- 死亡背包的容量為動態計算：`Math.max(1, Math.min(6, ceil(itemCount / 9.0)))` 排
- `BackpackItemHelper` 統一判斷 `TieredBackpackItem` 與 `DeathBackpackItem`，並負責把 `DataComponents.CONTAINER` 內物品生成回世界
- `ItemEntityMixin` 攔截背包物品實體被傷害破壞與自然時間到消失，於原本 `discard()` 前掉出內部物品
- 爆炸等有來源位置的傷害會用 `DamageSource.getSourcePosition()` 計算反方向；仙人掌會搜尋附近 `Blocks.CACTUS` 作為來源；自然時間到則以移動方向反向或隨機方向散開

#### 豬糞與硝石煉製
- `PigMixin` 在豬的 `registerGoals()` 結尾加入 `PigManureGoal`，優先級與羊的 `EatBlockGoal` 同為 5
- `PigManureGoal` 沿用羊吃草的觸發機率：成年 1/1000、幼年 1/50；受 `mobGriefing` 控制
- 豬吃草觸發時會把腳下可支援的泥土系列方塊換成對應的 `PigManureBlock`
- `PigManureBlock` 會記住原本的乾淨方塊狀態，鏟子右鍵採集後可還原原方塊
- `AlchemyHandler.register()` 註冊 `UseBlockCallback` 與 server tick：
  - 鏟子右鍵帶豬糞方塊：還原方塊並給玩家 1 個 `pig_manure`
  - 手持木灰、紅/棕蘑菇、豬糞右鍵滿水煉藥鍋：投入一份材料
  - 每 5 tick 掃描掉進煉藥鍋的物品實體，支援把材料直接丟進鍋裡
- 第一份材料投入滿水煉藥鍋時，方塊會替換成 `alchemy_cauldron`
- `AlchemyCauldronBlockEntity` 儲存三種材料的「已投入 / 已煮入」狀態與目前烹煮時間
- 只有煉藥鍋正下方是已點燃的營火或靈魂營火時才會推進烹煮
- 每 200 tick 完成一份材料，並消耗 1 層水；三份材料完成後生成 1 個硝石並把方塊恢復成空煉藥鍋
- `saltpeter.json` 與 `gunpowder_from_sulfur.json` 已移除，避免繞過新的硝石流程與取消的煉金法 2

#### 銅魁儡綁定分類
- `CopperWrenchItem` 只負責物品型別，本身不直接處理互動邏輯
- `CopperGolemWrenchHandler.register()` 註冊 Fabric `AttackEntityCallback`、`AttackBlockCallback`、`UseEntityCallback` 與 `UseBlockCallback`
- 銅板手點擊流程：
  - 左鍵銅魁儡：把銅魁儡 UUID 暫存在扳手 `DataComponents.CUSTOM_DATA`，並取消原本攻擊傷害
  - 左鍵容器：若扳手已選取銅魁儡，從該銅魁儡的 `deadrecall_bound_containers` 移除目前容器座標
  - Shift+左鍵銅魁儡：讀取銅魁儡 `DataComponents.CUSTOM_DATA` 的綁定清單，對所有同維度且可用容器顯示粒子路徑
  - 右鍵容器：把容器維度與座標寫入目前選取銅魁儡的 `DataComponents.CUSTOM_DATA`
  - 寫入完成後保留扳手上的銅魁儡 UUID，方便連續綁定多個容器
  - Shift+右鍵銅魁儡：server 送出 `CopperWrenchBindingsPayload`，client 開啟 `CopperWrenchBindingsScreen`
  - `CopperWrenchBindingsScreen` 以自訂選項畫面顯示綁定清單，每列包含容器 icon、block id、維度、座標與狀態
  - UI 右上角按鈕送出 `CopperGolemOperationPayload`，切換 `deadrecall_transport_enabled`
- 銅魁儡永久綁定資料使用 `deadrecall_bound_containers` 清單儲存
- 銅魁儡運作狀態使用 `deadrecall_transport_enabled` 儲存；未寫入時預設為運作中
- 舊版單一容器資料 `deadrecall_bound_container_*` 仍會讀取並遷移到新清單格式
- `CopperGolemWrenchHandler.tickCopperGolemWrenchState()` 由 server tick 呼叫，用來清理失效綁定、處理全箱無法分類時的原地跳躍與自動解除
- 失效綁定清理每 20 tick 執行一次；若目標維度存在、目標 chunk 已載入，但該座標已無法建立 `TransportItemTarget`，或該方塊屬於 `BlockTags.COPPER_CHESTS`，就會從 `deadrecall_bound_containers` 移除
- 綁定目的地解析使用 `tryCreateBoundTarget()`，會排除 `BlockTags.COPPER_CHESTS`，避免把銅魁儡來源容器當成分類目的地
- `TransportItemsBetweenContainersMixin` 攔截原版銅魁儡搬運行為：
  - `checkExtraStartConditions`：沒有綁定容器、已停止或正在阻塞跳躍的銅魁儡不啟動搬運行為
  - `getTransportTarget`：手上有物品時，依綁定順序找下一個未嘗試的分類容器
  - `pickUpItems`：手上沒物品時，先判斷整個銅箱子是否仍有可分類物，再從 slot 0 往後拿第一個非空格物品
  - `putDownItem`：物品成功放下後，清除本次來源槽位記錄
  - `isWantedBlock`：讓已綁定銅魁儡在手持物品時接受綁定容器作為有效目標
- `deadrecall_tried_destinations` 會記錄同一個手上物品已經試過哪些綁定容器
- 分類容器必須已存在相同物品與相同 Data Components；空箱不會被視為分類目標
- 若銅魁儡已拿起物品但所有綁定容器都找不到可分類目的地，會依 `deadrecall_source_container_*` 與 `deadrecall_source_slot` 把物品放回來源銅箱子後段
- 回放時若後段沒有空槽或可堆疊位置，會把來源槽位後方物品往前位移，並把無法分類的物品放到最後面
- 若來源銅箱子內所有物品都找不到可分類容器，會寫入 `deadrecall_sorting_blocked`
- 阻塞快照包含 `deadrecall_blocked_source_hash`、`deadrecall_blocked_bindings_hash`、`deadrecall_blocked_targets_hash`
- 阻塞期間銅魁儡會停止導航、清除 `WALK_TARGET`，並每 10 tick 嘗試跳起一次
- 來源銅箱子內容、綁定清單或綁定目標容器內容任一 hash 改變時，會清除阻塞 tag 並重置搬運記憶

### 待辦 / 已知問題
- ⚠️ `/back` 指令未處理跨維度傳送（如從地獄傳回主世界）
- ⚠️ 死亡背包收集範圍未過濾所有者，多人同時死亡可能互相收集到對方物品
- ⚠️ 舊版 `deadrecall:backpack` 物品 ID 仍保留但標記為 `@Deprecated`
- ⚠️ 銅板手目前沒有生存合成配方
- ⚠️ 銅魁儡綁定容器尚未實作一鍵清空；單一容器可用銅板手左鍵解除，失效容器會自動移除
- ⚠️ 綁定容器跨維度時不會被本次搬運使用

### 構建指令
```bash
# 編譯
./gradlew compileJava

# 完整構建（產出 .jar）
./gradlew build

# 資料生成
./gradlew runDatagen
```
