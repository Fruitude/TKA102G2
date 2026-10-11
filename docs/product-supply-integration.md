# 商品供應規則整合入口

商品團隊的規則集中於 `com.fruitude.product.model`。`PoService`、`ReceivingService`、`PoReviewService` 保留組員的版本，沒有加入商品邏輯，也沒有更改其採購、驗收、價格或庫存 SQL。

## Service 與入口

| 商品 Service | 職責 | 接入入口 |
| --- | --- | --- |
| `SkuSupplyService` | 最近90天驗收良率、供應量、購買上限及規格狀態判斷 | 原有商品生命週期、訂單及排程 |
| `ProductProcurementService` | 良率不足清單、建議採購量、扣除其他待審核單、新增／修改採購表單預填 | `PoController`、`PoNoController` 的採購建議讀取方法 |
| `ProductPurchaseFlowService` | 在同一交易中呼叫組員審核／驗收，再同步商品與規格狀態 | `PoReviewController.approve`、`ReceivingController.update` 的寫入呼叫 |
| `ProductInventoryLockService` | 鎖商品及規格、重新讀取最新數量，避免持久化舊數量 | 商品／規格修改、整頁上下架、商品排程及上述採購流程 |

新增其他驗收或審核入口時，呼叫 `ProductPurchaseFlowService.receive(form, employee, receivedContent)` 或 `approve(poId, reviewedContent)`。直接呼叫組員 Service 仍只執行組員原有業務，不會套用商品狀態同步。

## 良率與採購

新版首次驗收結案為 `po_status=4`；已驗收舊紀錄 `po_status=1` 同樣納入。驗收結果 `inbound_status=1/2/3` 才能進入樣本：成功採實際到貨／不良品，失敗或取消驗收採購數量全數算不良品。待審核採購申請取消 (`po_status=3`、尚未驗收) 不代表供應商驗收失敗，不計入良率。取消驗收沒有驗收日期時，以採購日期作為相容備援。

預估可供應量 A＝庫存＋待進貨×良率－待出貨；A 小於安全庫存的販售監控規格需要採購。

建議採購量＝ceil(max(0, 安全庫存＋採購溢額－A)／良率)－其他待審核採購量，最少0、最多9999。正在編輯的待審核單數量不重複扣除。良率0不除以0，顯示人工確認，不自動產生建議數量。

`ProductSku.purchaseYield` 為非資料庫欄位，只由商品採購查詢附上估計值，用於採購缺口的顯示；不新增資料表或資料庫欄位。

## 驗收與恢復

同一交易依序：鎖採購單 → 鎖商品及規格 → 讀取最新數量 → 執行組員驗收 → 寫入驗收歷史 → 重新讀取批次 SQL 更新後的規格 → 同步商品狀態。

只有良品庫存增加的規格會觸發售完恢復，包括修改验收紀錄時的正差額。重送舊摘要由組員原本的檢查阻擋；修改後數量相同、減少、驗收失敗或取消不當成補貨。手動下架優先、即將停產6的限制仍保留。審核增加待進貨會重算販售規格，但不當成实际入庫，因此不會僅因新增待進貨解除售完。

組員審核的條件更新會清除 EntityManager；商品整合層會重新取得受管理的實體後再同步，資料庫鎖仍由同一交易持有。商品／庫存狀態和採購驗收任一步失敗，整筆交易回滾。
